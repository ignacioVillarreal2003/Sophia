package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.dto.request.ChangeEmailRequest;
import com.sophia.authentication.domain.entity.EmailVerificationToken;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.enums.TokenPurpose;
import com.sophia.authentication.domain.exception.BadRequestException;
import com.sophia.authentication.domain.exception.DuplicateResourceException;
import com.sophia.authentication.domain.exception.InvalidCredentialsException;
import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.domain.exception.OperationNotAllowedException;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.infrastructure.messaging.UserEventPublisher;
import com.sophia.authentication.infrastructure.messaging.UserEventType;
import com.sophia.authentication.infrastructure.repository.EmailVerificationTokenRepository;
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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationProperties properties;
    private final UserEventPublisher userEventPublisher;

    @Transactional
    public void sendVerificationToken(User user) {
        tokenRepository.invalidateAllByUserIdAndPurpose(user.getId(), TokenPurpose.EMAIL_VERIFICATION, Instant.now());
        String rawToken = TokenHasher.generateToken();
        tokenRepository.save(EmailVerificationToken.create(
                user, TokenHasher.sha256Hex(rawToken), TokenPurpose.EMAIL_VERIFICATION, null,
                Instant.now().plus(properties.security().emailVerificationTokenTtl())));
        emailService.sendVerificationEmail(user, rawToken);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = findUsableToken(rawToken, TokenPurpose.EMAIL_VERIFICATION);
        User user = token.getUser();
        user.verifyEmail();
        token.markUsed();
        auditService.success(AuditActions.EMAIL_VERIFIED, user.getId(), user.getEmail());
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresentOrElse(user -> {
                    sendVerificationToken(user);
                    auditService.success(AuditActions.VERIFICATION_RESENT, user.getId(), user.getEmail());
                }, () -> log.debug("Verification resend requested for unknown or verified email"));
    }

    @Transactional
    public void adminResendVerification(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }
        sendVerificationToken(user);
        auditService.record(null, null, AuditActions.VERIFICATION_RESENT, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, null);
    }

    @Transactional
    public void adminVerifyEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }
        user.verifyEmail();
        auditService.record(null, null, AuditActions.EMAIL_VERIFIED, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, null);
    }

    @Transactional
    public void adminUnverifyEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!user.isEmailVerified()) {
            throw new BadRequestException("Email is already unverified");
        }
        user.unverifyEmail();
        auditService.record(null, null, AuditActions.EMAIL_UNVERIFIED_BY_ADMIN, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, null);
    }

    @Transactional
    public void requestEmailChange(UUID userId, ChangeEmailRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getPassword() == null) {
            throw new OperationNotAllowedException(
                    "Set a password before changing the email of an OAuth-only account");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        String newEmail = request.newEmail().toLowerCase().trim();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new OperationNotAllowedException("The new email matches the current one");
        }
        if (userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        tokenRepository.invalidateAllByUserIdAndPurpose(userId, TokenPurpose.EMAIL_CHANGE, Instant.now());
        String rawToken = TokenHasher.generateToken();
        tokenRepository.save(EmailVerificationToken.create(
                user, TokenHasher.sha256Hex(rawToken), TokenPurpose.EMAIL_CHANGE, newEmail,
                Instant.now().plus(properties.security().emailVerificationTokenTtl())));

        emailService.sendEmailChangeConfirmation(user, newEmail, rawToken);
        auditService.record(user.getId(), user.getEmail(), AuditActions.EMAIL_CHANGE_REQUESTED, "User",
                userId.toString(), AuditLog.Result.SUCCESS, Map.of("newEmail", newEmail));
    }

    @Transactional
    public void confirmEmailChange(String rawToken) {
        EmailVerificationToken token = findUsableToken(rawToken, TokenPurpose.EMAIL_CHANGE);
        User user = token.getUser();
        String oldEmail = user.getEmail();
        String newEmail = token.getNewEmail();

        if (userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        user.changeEmail(newEmail);
        user.verifyEmail();
        token.markUsed();
        auditService.record(user.getId(), newEmail, AuditActions.EMAIL_CHANGED, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, Map.of("oldEmail", oldEmail));
        userEventPublisher.publish(UserEventType.USER_EMAIL_CHANGED, user.getId(), newEmail);
    }

    private EmailVerificationToken findUsableToken(String rawToken, TokenPurpose purpose) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));
        if (token.getPurpose() != purpose || !token.isUsable()) {
            throw new InvalidTokenException("Invalid or expired token");
        }
        return token;
    }
}
