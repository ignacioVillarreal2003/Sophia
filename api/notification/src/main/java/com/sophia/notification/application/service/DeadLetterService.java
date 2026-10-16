package com.sophia.notification.application.service;

import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.domain.dto.response.DeadLetterReplayResponse;
import com.sophia.notification.domain.entity.AuditLog;
import com.sophia.notification.infrastructure.service.DeadLetterReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Administration entry point for dead letter recovery: drives the Kafka replay
 * adapter and records the privileged action in the audit trail.
 */
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private static final String RESOURCE_TYPE = "KafkaTopic";

    private final DeadLetterReplayService replayService;
    private final AuditService auditService;

    public DeadLetterReplayResponse replay(String topic, int max, AuthenticatedUser actor) {
        DeadLetterReplayService.ReplayResult result = replayService.replay(topic, max);
        auditService.record(actor.id(), actor.email(), AuditService.REPLAY_DEAD_LETTERS, RESOURCE_TYPE, topic,
                AuditLog.Result.SUCCESS, AuditService.details("replayed", result.replayed()));
        return new DeadLetterReplayResponse(result.topic(), result.replayed());
    }
}
