package com.sophia.documentworkspace.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.entity.OutboxEvent;
import com.sophia.documentworkspace.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enqueues notification commands for the notification microservice through the
 * transactional outbox, so a rolled-back business change never notifies anyone
 * and a committed one is delivered at least once.
 */
@Component
@RequiredArgsConstructor
public class NotificationCommandPublisher {

    private static final String MESSAGE_TYPE = "notification-command";

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(NotificationCommandPayload payload) {
        outboxEventRepository.save(OutboxEvent.forTopic(
                properties.events().notificationCommandsTopic(),
                MESSAGE_TYPE,
                payload.partitionKey(),
                payload.messageId(),
                serialize(payload),
                MessageCorrelation.current()));
    }

    private String serialize(NotificationCommandPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification command", e);
        }
    }
}
