package com.sophia.notification.infrastructure.service;

import com.sophia.notification.infrastructure.repository.AuditLogRepository;
import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
import com.sophia.notification.infrastructure.repository.NotificationDispatchRepository;
import com.sophia.notification.infrastructure.repository.NotificationRepository;
import com.sophia.notification.infrastructure.repository.ProcessedMessageRepository;
import com.sophia.notification.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenanceServiceTest {

    private static final int BATCH_SIZE = 1_000;

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationDispatchRepository dispatchRepository;
    @Mock
    private EmailOutboxRepository emailOutboxRepository;
    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    private MaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceService(auditLogRepository, notificationRepository, dispatchRepository,
                emailOutboxRepository, processedMessageRepository, TestFixtures.applicationProperties());
    }

    @Test
    void aPartialBatchEndsThePurgeForThatStatement() {
        when(notificationRepository.deleteBatchOfReadBefore(any(), anyInt())).thenReturn(10);

        service.purgeExpiredData();

        verify(notificationRepository, times(1)).deleteBatchOfReadBefore(any(), anyInt());
    }

    @Test
    void fullBatchesKeepGoingUntilTheTableIsDrained() {
        when(notificationRepository.deleteBatchOfReadBefore(any(), anyInt()))
                .thenReturn(BATCH_SIZE, BATCH_SIZE, 42);

        service.purgeExpiredData();

        verify(notificationRepository, times(3)).deleteBatchOfReadBefore(any(), anyInt());
    }

    @Test
    void aBacklogLargerThanOneNightIsCappedInsteadOfRunningForever() {
        when(notificationRepository.deleteBatchOfReadBefore(any(), anyInt())).thenReturn(BATCH_SIZE);

        service.purgeExpiredData();

        verify(notificationRepository, times(500)).deleteBatchOfReadBefore(any(), anyInt());
    }

    @Test
    void everyRetainedTableIsPurged() {
        service.purgeExpiredData();

        verify(notificationRepository).softDeleteBatchOfExpired(any(), anyInt());
        verify(notificationRepository).deleteBatchOfReadBefore(any(), anyInt());
        verify(notificationRepository).deleteBatchOfSoftDeletedBefore(any(), anyInt());
        verify(notificationRepository).deleteBatchOfCreatedBefore(any(), anyInt());
        verify(emailOutboxRepository).deleteBatchOfCompletedBefore(any(), anyInt());
        verify(dispatchRepository).deleteBatchOfTerminalBefore(any(), anyInt());
        verify(processedMessageRepository).deleteBatchOfProcessedBefore(any(), anyInt());
        verify(auditLogRepository).deleteBatchOfEntriesBefore(any(), anyInt());
    }

    @Test
    void eachStatementIsGivenItsConfiguredThreshold() {
        Instant before = Instant.now();

        service.purgeExpiredData();

        ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteBatchOfReadBefore(threshold.capture(), anyInt());
        assertThat(threshold.getValue()).isBefore(before.minusSeconds(89 * 24 * 3600));
    }
}
