package com.sophia.notification.infrastructure.service;

import com.sophia.notification.domain.config.ApplicationProperties;
import com.sophia.notification.infrastructure.repository.AuditLogRepository;
import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
import com.sophia.notification.infrastructure.repository.NotificationDispatchRepository;
import com.sophia.notification.infrastructure.repository.NotificationRepository;
import com.sophia.notification.infrastructure.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.function.IntSupplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceService {

    private static final int BATCH_SIZE = 1_000;
    private static final int MAX_BATCHES = 500;

    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDispatchRepository dispatchRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ApplicationProperties properties;

    @Scheduled(cron = "0 15 3 * * *")
    @SchedulerLock(name = "notification-maintenance", lockAtMostFor = "PT30M")
    public void purgeExpiredData() {
        Instant now = Instant.now();
        Instant readThreshold = now.minus(properties.retention().readNotifications());

        int justExpired = inBatches("expired notifications",
                () -> notificationRepository.softDeleteBatchOfExpired(now, BATCH_SIZE));
        int readNotifications = inBatches("read notifications",
                () -> notificationRepository.deleteBatchOfReadBefore(readThreshold, BATCH_SIZE));
        int softDeleted = inBatches("soft-deleted notifications",
                () -> notificationRepository.deleteBatchOfSoftDeletedBefore(readThreshold, BATCH_SIZE));
        int oldNotifications = inBatches("stale notifications",
                () -> notificationRepository.deleteBatchOfCreatedBefore(
                        now.minus(properties.retention().notifications()), BATCH_SIZE));
        int completedEmails = inBatches("completed emails",
                () -> emailOutboxRepository.deleteBatchOfCompletedBefore(
                        now.minus(properties.retention().completedEmails()), BATCH_SIZE));
        int completedDispatches = inBatches("dispatch records",
                () -> dispatchRepository.deleteBatchOfTerminalBefore(
                        now.minus(properties.retention().completedEmails()), BATCH_SIZE));
        int processedMessages = inBatches("processed message records",
                () -> processedMessageRepository.deleteBatchOfProcessedBefore(
                        now.minus(properties.retention().processedMessages()), BATCH_SIZE));
        int auditEntries = inBatches("audit entries",
                () -> auditLogRepository.deleteBatchOfEntriesBefore(
                        now.minus(properties.retention().auditLog()), BATCH_SIZE));

        if (justExpired + readNotifications + softDeleted + oldNotifications + completedEmails
                + completedDispatches + processedMessages + auditEntries > 0) {
            log.info("Maintenance purge expired {} notifications, removed {} read, {} soft-deleted and "
                            + "{} stale notifications, {} completed emails, {} dispatch records, "
                            + "{} processed message records and {} audit entries",
                    justExpired, readNotifications, softDeleted, oldNotifications,
                    completedEmails, completedDispatches, processedMessages, auditEntries);
        }
    }

    private int inBatches(String what, IntSupplier statement) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int affected = statement.getAsInt();
            total += affected;
            if (affected < BATCH_SIZE) {
                return total;
            }
        }
        log.warn("Stopped purging {} after {} batches ({} rows); the rest waits for the next run",
                what, MAX_BATCHES, total);
        return total;
    }
}
