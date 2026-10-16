package com.sophia.authentication.application.service;

import com.sophia.authentication.application.service.MaintenanceService;
import com.sophia.authentication.infrastructure.repository.AuditLogRepository;
import com.sophia.authentication.infrastructure.repository.EmailVerificationTokenRepository;
import com.sophia.authentication.infrastructure.repository.LoginAttemptRepository;
import com.sophia.authentication.infrastructure.repository.NotificationCommandOutboxRepository;
import com.sophia.authentication.infrastructure.repository.PasswordResetTokenRepository;
import com.sophia.authentication.infrastructure.repository.RefreshTokenRepository;
import com.sophia.authentication.infrastructure.repository.UserEventOutboxRepository;
import com.sophia.authentication.infrastructure.repository.UserSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserEventOutboxRepository userEventOutboxRepository;
    @Mock private NotificationCommandOutboxRepository notificationCommandOutboxRepository;

    @InjectMocks
    private MaintenanceService maintenanceService;

    @Test
    @DisplayName("the nightly purge trims every expirable table including audit logs and outboxes")
    void purgesAllArtifacts() {
        maintenanceService.purgeExpiredArtifacts();

        verify(refreshTokenRepository).deleteAllExpiredBefore(any(Instant.class));
        verify(userSessionRepository).deleteAllExpiredBefore(any(Instant.class));
        verify(passwordResetTokenRepository).deleteAllExpiredBefore(any(Instant.class));
        verify(emailVerificationTokenRepository).deleteAllExpiredBefore(any(Instant.class));
        verify(loginAttemptRepository).deleteAllBefore(any(Instant.class));
        verify(userEventOutboxRepository).deleteAllPublishedBefore(any(Instant.class));
        verify(notificationCommandOutboxRepository).deleteAllPublishedBefore(any(Instant.class));

        ArgumentCaptor<Instant> auditThreshold = ArgumentCaptor.forClass(Instant.class);
        verify(auditLogRepository).deleteAllBefore(auditThreshold.capture());
        assertThat(auditThreshold.getValue())
                .isBefore(Instant.now().minus(Duration.ofDays(179)))
                .isAfter(Instant.now().minus(Duration.ofDays(181)));
    }
}
