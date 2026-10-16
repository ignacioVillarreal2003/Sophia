package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.dto.request.ChangePasswordRequest;
import com.sophia.authentication.domain.entity.PasswordHistory;
import com.sophia.authentication.domain.entity.PasswordResetToken;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.exception.BadRequestException;
import com.sophia.authentication.domain.exception.InvalidCredentialsException;
import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.domain.exception.OperationNotAllowedException;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.infrastructure.repository.PasswordHistoryRepository;
import com.sophia.authentication.infrastructure.repository.PasswordResetTokenRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.service.EmailService;
import com.sophia.authentication.domain.util.AuditActions;
import com.sophia.authentication.domain.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final ApplicationProperties properties;

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
            resetTokenRepository.invalidateAllByUserId(user.getId(), Instant.now());
            String rawToken = TokenHasher.generateToken();
            resetTokenRepository.save(PasswordResetToken.create(
                    user, TokenHasher.sha256Hex(rawToken),
                    Instant.now().plus(properties.security().passwordResetTokenTtl())));
            emailService.sendPasswordResetEmail(user, rawToken);
            auditService.success(AuditActions.PASSWORD_RESET_REQUESTED, user.getId(), user.getEmail());
        }, () -> log.debug("Password reset requested for unknown email"));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));
        if (!token.isUsable()) {
            throw new InvalidTokenException("Invalid or expired token");
        }

        User user = token.getUser();
        assertPasswordNotReused(user, newPassword);
        archiveCurrentPassword(user);
        user.changePassword(passwordEncoder.encode(newPassword));
        user.unlock();
        token.markUsed();

        tokenService.revokeAllForUser(user.getId());
        emailService.sendPasswordChangedNotice(user);
        auditService.success(AuditActions.PASSWORD_RESET, user.getId(), user.getEmail());
    }

    @Transactional
    public void changePassword(UUID userId, UUID currentSessionId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getPassword() == null) {
            throw new OperationNotAllowedException(
                    "This account has no password yet; use the password reset flow to set one");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        assertPasswordNotReused(user, request.newPassword());
        archiveCurrentPassword(user);
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        tokenService.revokeAllForUserExcept(userId, currentSessionId);
        emailService.sendPasswordChangedNotice(user);
        auditService.success(AuditActions.PASSWORD_CHANGED, user.getId(), user.getEmail());
    }

    @Transactional
    public void adminSetPassword(UUID targetUserId, String newPassword) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));

        assertPasswordNotReused(user, newPassword);
        archiveCurrentPassword(user);
        user.changePassword(passwordEncoder.encode(newPassword));
        user.unlock();

        tokenService.revokeAllForUser(user.getId());
        emailService.sendPasswordChangedNotice(user);
        auditService.record(null, null, AuditActions.USER_PASSWORD_RESET_BY_ADMIN, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, Map.of("email", user.getEmail()));
    }

    private void assertPasswordNotReused(User user, String newPassword) {
        int historyCount = properties.security().password().historyCount();
        if (historyCount <= 0) {
            return;
        }
        if (user.getPassword() != null && passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("New password must differ from your recent passwords");
        }
        boolean reused = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .limit(historyCount)
                .anyMatch(entry -> passwordEncoder.matches(newPassword, entry.getPasswordHash()));
        if (reused) {
            throw new BadRequestException("New password must differ from your recent passwords");
        }
    }

    private void archiveCurrentPassword(User user) {
        int historyCount = properties.security().password().historyCount();
        if (historyCount <= 0 || user.getPassword() == null) {
            return;
        }
        passwordHistoryRepository.save(PasswordHistory.create(user.getId(), user.getPassword()));
        List<PasswordHistory> entries = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (entries.size() > historyCount) {
            passwordHistoryRepository.deleteAll(entries.subList(historyCount, entries.size()));
        }
    }
}
