package com.sophia.notification.domain.entity;

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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOutboxMessage {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "dispatch_id", updatable = false)
    private UUID dispatchId;

    @Column(name = "category", updatable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static EmailOutboxMessage create(UUID dispatchId, String category, String recipient, String subject,
                                            String body, String correlationId, Instant nextAttemptAt) {
        EmailOutboxMessage message = new EmailOutboxMessage();
        message.dispatchId = dispatchId;
        message.category = category;
        message.recipient = recipient;
        message.subject = subject;
        message.body = body;
        message.correlationId = correlationId;
        message.nextAttemptAt = nextAttemptAt;
        return message;
    }

    public int claimAttempt(Duration baseBackoff) {
        attempts = attempts + 1;
        nextAttemptAt = Instant.now().plus(baseBackoff.multipliedBy(attempts));
        return attempts;
    }

    public void markSent(String deliveredBody) {
        publishedAt = Instant.now();
        lastError = null;
        body = deliveredBody;
    }

    public void markFailed(String error) {
        lastError = truncate(error);
    }

    public void discard(String reason) {
        publishedAt = Instant.now();
        lastError = truncate(reason);
    }

    private static String truncate(String error) {
        return error != null && error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
