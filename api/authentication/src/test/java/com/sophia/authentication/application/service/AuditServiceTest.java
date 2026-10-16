package com.sophia.authentication.application.service;

import com.sophia.authentication.api.filter.CorrelationIdFilter;
import com.sophia.authentication.application.mapper.AuditLogMapper;
import com.sophia.authentication.application.service.AuditLogPersister;
import com.sophia.authentication.application.service.AuditService;
import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.util.AuditActions;
import com.sophia.authentication.infrastructure.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditLogPersister auditLogPersister;

    private final AuditLogMapper auditLogMapper = Mappers.getMapper(AuditLogMapper.class);

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("record captures the caller context and hands the entry to the async persister")
    void recordDelegatesToPersister() {
        MDC.put(CorrelationIdFilter.REQUEST_ID_MDC_KEY, "request-42");
        AuditService auditService = new AuditService(auditLogRepository, auditLogMapper, auditLogPersister);
        UUID userId = UUID.randomUUID();

        auditService.record(userId, "jane@test.local", AuditActions.LOGIN_FAILED, null, null, AuditLog.Result.FAILURE,
                Map.of("reason", "BAD_CREDENTIALS"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPersister).persist(captor.capture());
        AuditLog entry = captor.getValue();
        assertThat(entry.getAction()).isEqualTo(AuditActions.LOGIN_FAILED);
        assertThat(entry.getResult()).isEqualTo(AuditLog.Result.FAILURE);
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.getUserEmail()).isEqualTo("jane@test.local");
        assertThat(entry.getDetails()).containsEntry("reason", "BAD_CREDENTIALS");
        assertThat(entry.getRequestId()).isEqualTo("request-42");
    }

    @Test
    @DisplayName("record never propagates persister failures to the caller")
    void recordSwallowsDispatchFailures() {
        doThrow(new IllegalStateException("executor rejected")).when(auditLogPersister).persist(any());
        AuditService auditService = new AuditService(auditLogRepository, auditLogMapper, auditLogPersister);

        assertThatCode(() -> auditService.success(AuditActions.LOGIN, UUID.randomUUID(), "jane@test.local"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the persister swallows storage failures instead of breaking business flows")
    void persisterSwallowsStorageFailures() {
        doThrow(new IllegalStateException("connection pool exhausted"))
                .when(auditLogRepository).save(any());
        AuditLogPersister persister = new AuditLogPersister(auditLogRepository);

        assertThatCode(() -> persister.persist(AuditLog.of(
                null, null, AuditActions.LOGIN, null, null, AuditLog.Result.SUCCESS, null, null, null, null)))
                .doesNotThrowAnyException();
    }
}
