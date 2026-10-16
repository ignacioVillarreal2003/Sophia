package com.sophia.authentication.application.service;

import com.sophia.authentication.infrastructure.repository.AuditLogRepository;
import com.sophia.authentication.infrastructure.repository.EmailVerificationTokenRepository;
import com.sophia.authentication.infrastructure.repository.LoginAttemptRepository;
import com.sophia.authentication.infrastructure.repository.NotificationCommandOutboxRepository;
import com.sophia.authentication.infrastructure.repository.PasswordResetTokenRepository;
import com.sophia.authentication.infrastructure.repository.RefreshTokenRepository;
import com.sophia.authentication.infrastructure.repository.UserEventOutboxRepository;
import com.sophia.authentication.infrastructure.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceService {

    private static final Duration RETENTION = Duration.ofDays(30);
    private static final Duration AUDIT_LOG_RETENTION = Duration.ofDays(180);
    private static final Duration PUBLISHED_EVENT_RETENTION = Duration.ofDays(7);
    private static final Duration NOTIFICATION_COMMAND_RETENTION = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserEventOutboxRepository userEventOutboxRepository;
    private final NotificationCommandOutboxRepository notificationCommandOutboxRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "maintenance-purge", lockAtMostFor = "PT30M")
    @Transactional
    public void purgeExpiredArtifacts() {
        Instant threshold = Instant.now().minus(RETENTION);
        int refreshTokens = refreshTokenRepository.deleteAllExpiredBefore(threshold);
        int sessions = userSessionRepository.deleteAllExpiredBefore(threshold);
        int resetTokens = passwordResetTokenRepository.deleteAllExpiredBefore(threshold);
        int verificationTokens = emailVerificationTokenRepository.deleteAllExpiredBefore(threshold);
        int loginAttempts = loginAttemptRepository.deleteAllBefore(threshold);
        int auditLogs = auditLogRepository.deleteAllBefore(Instant.now().minus(AUDIT_LOG_RETENTION));
        int publishedEvents = userEventOutboxRepository
                .deleteAllPublishedBefore(Instant.now().minus(PUBLISHED_EVENT_RETENTION));
        int notificationCommands = notificationCommandOutboxRepository
                .deleteAllPublishedBefore(Instant.now().minus(NOTIFICATION_COMMAND_RETENTION));
        log.info("Maintenance purge: {} refresh tokens, {} sessions, {} reset tokens, "
                        + "{} verification tokens, {} login attempts, {} audit logs, "
                        + "{} published events, {} notification commands",
                refreshTokens, sessions, resetTokens, verificationTokens, loginAttempts, auditLogs,
                publishedEvents, notificationCommands);
    }
}
