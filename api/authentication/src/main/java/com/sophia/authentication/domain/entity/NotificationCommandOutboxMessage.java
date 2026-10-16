package com.sophia.authentication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_command_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationCommandOutboxMessage {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_key", nullable = false, length = 255)
    private String recipientKey;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static NotificationCommandOutboxMessage create(String recipientKey, String correlationId, String payload,
                                                           Instant occurredAt) {
        NotificationCommandOutboxMessage message = new NotificationCommandOutboxMessage();
        message.recipientKey = recipientKey;
        message.correlationId = correlationId;
        message.payload = payload;
        message.occurredAt = occurredAt;
        return message;
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
        this.attempts = this.attempts + 1;
        this.lastError = truncate(error);
    }

    private static String truncate(String error) {
        return error != null && error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
