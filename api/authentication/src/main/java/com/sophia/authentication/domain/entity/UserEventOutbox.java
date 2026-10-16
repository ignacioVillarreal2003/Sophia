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
@Table(name = "user_event_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEventOutbox {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "locale", length = 10)
    private String locale;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

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

    public static UserEventOutbox create(String eventType, UUID userId, String email, String firstName,
                                         String lastName, String locale, String correlationId, Instant occurredAt) {
        UserEventOutbox outbox = new UserEventOutbox();
        outbox.eventType = eventType;
        outbox.userId = userId;
        outbox.email = email;
        outbox.firstName = firstName;
        outbox.lastName = lastName;
        outbox.locale = locale;
        outbox.correlationId = correlationId;
        outbox.occurredAt = occurredAt;
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
        this.attempts = this.attempts + 1;
        this.lastError = truncate(error);
    }

    private static String truncate(String error) {
        return error != null && error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
