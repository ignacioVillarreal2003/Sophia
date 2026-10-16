package com.sophia.documentworkspace.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophia.documentworkspace.domain.entity.OutboxEvent;
import com.sophia.documentworkspace.domain.event.DomainEvent;
import com.sophia.documentworkspace.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for emitting domain events. {@code MANDATORY} propagation
 * enforces that events are always published inside the business transaction:
 * either the aggregate change and its events are committed together, or neither is.
 *
 * <p>The Kafka message body is a self-describing envelope so consumers can route
 * on {@code eventType} without inspecting the payload:
 * <pre>{@code
 * { "eventId": "...", "eventType": "workspace.created", "aggregateType": "workspace",
 *   "aggregateId": "...", "workspaceId": "...", "actorId": "...", "occurredAt": "...",
 *   "payload": { ...event specific fields... } }
 * }</pre>
 */
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        outboxEventRepository.save(OutboxEvent.of(event, envelopeOf(event), MessageCorrelation.current()));
        applicationEventPublisher.publishEvent(event);
    }

    private String envelopeOf(DomainEvent event) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", event.eventId().toString());
            envelope.put("eventType", event.eventType());
            envelope.put("aggregateType", event.aggregateType());
            envelope.put("aggregateId", event.aggregateId().toString());
            envelope.put("workspaceId", event.workspaceId() == null ? null : event.workspaceId().toString());
            envelope.put("actorId", event.actorId() == null ? null : event.actorId().toString());
            envelope.put("occurredAt", event.occurredAt().toString());
            envelope.set("payload", objectMapper.valueToTree(event));
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize domain event " + event.eventType(), e);
        }
    }
}
