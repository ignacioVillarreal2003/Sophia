package com.sophia.authentication.application.service;

import com.sophia.authentication.application.service.AuditService;
import com.sophia.authentication.application.service.PasswordService;
import com.sophia.authentication.application.service.TokenService;
import com.sophia.authentication.domain.dto.request.ChangePasswordRequest;
import com.sophia.authentication.domain.entity.PasswordHistory;
import com.sophia.authentication.domain.entity.PasswordResetToken;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.exception.BadRequestException;
import com.sophia.authentication.domain.exception.InvalidCredentialsException;
import com.sophia.authentication.domain.exception.InvalidTokenException;
import com.sophia.authentication.domain.util.TokenHasher;
import com.sophia.authentication.infrastructure.repository.PasswordHistoryRepository;
import com.sophia.authentication.infrastructure.repository.PasswordResetTokenRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.service.EmailService;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final String CURRENT_PASSWORD = "S3cure!Password";
    private static final String NEW_PASSWORD = "N3w!Password";
    private static final String OLD_PASSWORD = "Old!Password1";

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private TokenService tokenService;
    @Mock private EmailService emailService;
    @Mock private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private PasswordService passwordService;
    private User user;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(userRepository, resetTokenRepository, passwordHistoryRepository,
                passwordEncoder, tokenService, emailService, auditService, TestFixtures.appProperties());
        user = TestFixtures.user("jane@test.local", passwordEncoder.encode(CURRENT_PASSWORD));
    }

    @Test
    @DisplayName("changing the password rejects reusing the current password")
    void changeRejectsCurrentPassword() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordService.changePassword(user.getId(), UUID.randomUUID(),
                new ChangePasswordRequest(CURRENT_PASSWORD, CURRENT_PASSWORD)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("changing the password rejects passwords found in the history")
    void changeRejectsHistoricalPassword() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(PasswordHistory.create(user.getId(), passwordEncoder.encode(OLD_PASSWORD))));

        assertThatThrownBy(() -> passwordService.changePassword(user.getId(), UUID.randomUUID(),
                new ChangePasswordRequest(CURRENT_PASSWORD, OLD_PASSWORD)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("changing the password archives the old hash and revokes other sessions")
    void changeSuccess() {
        UUID sessionId = UUID.randomUUID();
        String previousHash = user.getPassword();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());

        passwordService.changePassword(user.getId(), sessionId,
                new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD));

        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPassword())).isTrue();
        verify(passwordHistoryRepository).save(org.mockito.ArgumentMatchers.argThat(entry ->
                entry.getUserId().equals(user.getId()) && entry.getPasswordHash().equals(previousHash)));
        verify(tokenService).revokeAllForUserExcept(user.getId(), sessionId);
        verify(emailService).sendPasswordChangedNotice(user);
    }

    @Test
    @DisplayName("changing the password requires the current password to match")
    void changeRejectsWrongCurrentPassword() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordService.changePassword(user.getId(), UUID.randomUUID(),
                new ChangePasswordRequest("Wrong!Pass1", NEW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetting with an unknown token fails")
    void resetRejectsUnknownToken() {
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordService.resetPassword("raw-token", NEW_PASSWORD))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("resetting rejects reusing a recent password")
    void resetRejectsReusedPassword() {
        String rawToken = TokenHasher.generateToken();
        PasswordResetToken token = PasswordResetToken.create(
                user, TokenHasher.sha256Hex(rawToken), Instant.now().plusSeconds(600));
        when(resetTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordService.resetPassword(rawToken, CURRENT_PASSWORD))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("resetting stores the new password, archives the old one and revokes sessions")
    void resetSuccess() {
        String rawToken = TokenHasher.generateToken();
        String previousHash = user.getPassword();
        PasswordResetToken token = PasswordResetToken.create(
                user, TokenHasher.sha256Hex(rawToken), Instant.now().plusSeconds(600));
        when(resetTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken)))
                .thenReturn(Optional.of(token));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());

        passwordService.resetPassword(rawToken, NEW_PASSWORD);

        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPassword())).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(passwordHistoryRepository).save(org.mockito.ArgumentMatchers.argThat(entry ->
                entry.getPasswordHash().equals(previousHash)));
        verify(tokenService).revokeAllForUser(user.getId());
    }

    @Test
    @DisplayName("forgot password stays silent for unknown emails")
    void forgotUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("ghost@test.local")).thenReturn(Optional.empty());

        passwordService.forgotPassword("ghost@test.local");

        verify(resetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), anyString());
    }

    @Test
    @DisplayName("forgot password issues a reset token and emails it")
    void forgotKnownEmail() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        passwordService.forgotPassword(user.getEmail());

        verify(resetTokenRepository).invalidateAllByUserId(org.mockito.ArgumentMatchers.eq(user.getId()), any());
        verify(resetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq(user), anyString());
    }
}
