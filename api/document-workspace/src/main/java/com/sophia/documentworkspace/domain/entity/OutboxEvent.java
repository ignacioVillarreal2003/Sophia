package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.event.DomainEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row. Written in the same transaction as the aggregate
 * change and relayed asynchronously to Kafka, guaranteeing at-least-once
 * delivery without dual-write inconsistencies.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "topic", length = 255)
    private String topic;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    public static OutboxEvent of(DomainEvent event, String payloadJson, String correlationId) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.id = event.eventId();
        outbox.aggregateType = event.aggregateType();
        outbox.aggregateId = event.aggregateId().toString();
        outbox.eventType = event.eventType();
        outbox.workspaceId = event.workspaceId();
        outbox.actorId = event.actorId();
        outbox.correlationId = correlationId;
        outbox.payload = payloadJson;
        outbox.createdAt = Instant.now();
        return outbox;
    }

    /** Row destined for another service's topic instead of the domain events stream. */
    public static OutboxEvent forTopic(String topic, String messageType, String partitionKey,
                                       UUID messageId, String payloadJson, String correlationId) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.id = messageId;
        outbox.aggregateType = messageType;
        outbox.aggregateId = partitionKey;
        outbox.eventType = messageType;
        outbox.topic = topic;
        outbox.correlationId = correlationId;
        outbox.payload = payloadJson;
        outbox.createdAt = Instant.now();
        return outbox;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void discard(String reason) {
        this.publishedAt = Instant.now();
        this.lastError = truncate(reason);
    }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = truncate(error);
    }

    private static String truncate(String error) {
        return error != null && error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
