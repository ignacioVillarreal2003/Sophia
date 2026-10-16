package com.sophia.notification.application.service;

import com.sophia.notification.api.filter.CorrelationIdFilter;
import com.sophia.notification.domain.entity.AuditLog;
import com.sophia.notification.infrastructure.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void anEntryCarriesTheUserResourceAndOriginatingRequest() {
        MDC.put(CorrelationIdFilter.REQUEST_ID_MDC_KEY, "req-42");

        service.record(userId, "actor@sophia.local", AuditService.READ_OTHER_INBOX,
                AuditService.RESOURCE_TYPE_USER, targetUserId.toString(), AuditLog.Result.SUCCESS,
                Map.of("category", "SYSTEM"));

        AuditLog entry = captured();
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.getUserEmail()).isEqualTo("actor@sophia.local");
        assertThat(entry.getAction()).isEqualTo("READ_OTHER_INBOX");
        assertThat(entry.getResourceType()).isEqualTo(AuditService.RESOURCE_TYPE_USER);
        assertThat(entry.getResourceId()).isEqualTo(targetUserId.toString());
        assertThat(entry.getDetails()).containsEntry("category", "SYSTEM");
        assertThat(entry.getRequestId()).isEqualTo("req-42");
    }

    @Test
    void defaultsToSuccessWhenCallerSaysSo() {
        service.record(userId, "actor@sophia.local", AuditService.READ_OTHER_INBOX,
                AuditService.RESOURCE_TYPE_USER, targetUserId.toString(), AuditLog.Result.SUCCESS,
                Map.of("category", "SYSTEM"));

        assertThat(captured().getResult()).isEqualTo(AuditLog.Result.SUCCESS);
    }

    @Test
    void aDeniedActionIsRecordedAsFailureWithItsResource() {
        service.record(userId, "actor@sophia.local", AuditService.REPLAY_DEAD_LETTERS, "KafkaTopic",
                "dead-letters.notifications", AuditLog.Result.FAILURE,
                Map.of("reason", "Access denied"));

        AuditLog entry = captured();
        assertThat(entry.getResult()).isEqualTo(AuditLog.Result.FAILURE);
        assertThat(entry.getAction()).isEqualTo("REPLAY_DEAD_LETTERS");
        assertThat(entry.getResourceType()).isEqualTo("KafkaTopic");
        assertThat(entry.getResourceId()).isEqualTo("dead-letters.notifications");
    }

    @Test
    void theClientIpAndUserAgentAreCapturedFromTheMdc() {
        MDC.put(CorrelationIdFilter.CLIENT_IP_MDC_KEY, "203.0.113.7");
        MDC.put(CorrelationIdFilter.USER_AGENT_MDC_KEY, "curl/8.0");

        service.record(userId, null, AuditService.READ_OTHER_PREFERENCES, null, null,
                AuditLog.Result.SUCCESS, Map.of());

        AuditLog entry = captured();
        assertThat(entry.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(entry.getUserAgent()).isEqualTo("curl/8.0");
    }

    @Test
    void anEmptyDetailsMapIsStoredAsNull() {
        service.record(userId, null, AuditService.READ_OTHER_PREFERENCES, null, null,
                AuditLog.Result.SUCCESS, Map.of());

        assertThat(captured().getDetails()).isNull();
    }

    @Test
    void anEntryWithoutARequestContextIsStillWritten() {
        service.record(userId, null, AuditService.REPLAY_DEAD_LETTERS, null, null,
                AuditLog.Result.SUCCESS, Map.of("topic", "x"));

        assertThat(captured().getRequestId()).isNull();
    }

    @Test
    void aFailingAuditWriteMustNotBreakTheActionItRecords() {
        when(auditLogRepository.save(any())).thenThrow(new DataIntegrityViolationException("audit table gone"));

        assertThatCode(() -> service.record(userId, null, AuditService.READ_OTHER_INBOX, null, null,
                AuditLog.Result.SUCCESS, Map.of()))
                .doesNotThrowAnyException();
    }

    private AuditLog captured() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
