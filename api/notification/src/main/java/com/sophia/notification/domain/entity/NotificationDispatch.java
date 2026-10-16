package com.sophia.notification.domain.entity;

import com.sophia.notification.domain.entity.enums.NotificationDispatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification_dispatches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "notification_id", updatable = false)
    private UUID notificationId;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(name = "recipient_id", updatable = false)
    private UUID recipientId;

    @Column(nullable = false, updatable = false, length = 20)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDispatchStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static NotificationDispatch create(UUID notificationId, UUID messageId, String correlationId,
                                              UUID recipientId, String channel) {
        NotificationDispatch dispatch = new NotificationDispatch();
        dispatch.notificationId = notificationId;
        dispatch.messageId = messageId;
        dispatch.correlationId = correlationId;
        dispatch.recipientId = recipientId;
        dispatch.channel = channel;
        dispatch.status = NotificationDispatchStatus.PENDING;
        return dispatch;
    }

    public void markSent() {
        status = NotificationDispatchStatus.SENT;
        sentAt = Instant.now();
    }

    public void markFailed(String error, int attempts) {
        status = NotificationDispatchStatus.FAILED;
        failedAt = Instant.now();
        lastError = error;
        this.attempts = attempts;
    }

    public void markAttemptFailed(String error, int attempts) {
        lastError = error;
        this.attempts = attempts;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        NotificationDispatch that = (NotificationDispatch) other;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
