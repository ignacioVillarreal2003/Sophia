package com.sophia.notification.domain.entity;

import com.sophia.notification.domain.entity.enums.NotificationSeverity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false, length = 80)
    private String category;

    @Column(nullable = false, updatable = false, length = 40)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private NotificationSeverity severity;

    @Column(nullable = false, updatable = false)
    private short priority;

    @Column(name = "group_key", updatable = false, length = 200)
    private String groupKey;

    @Column(name = "target_type", updatable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(nullable = false, updatable = false, length = 255)
    private String title;

    @Column(updatable = false, length = 2000)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private Map<String, String> data;

    @Column(name = "scheduled_at", updatable = false)
    private Instant scheduledAt;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Notification of(UUID recipientId, UUID createdBy, String category,
                                  String type, String title, String body, Map<String, String> data,
                                  NotificationAttributes attributes, Instant scheduledAt) {
        NotificationAttributes attrs = attributes == null ? NotificationAttributes.defaults() : attributes;
        Notification notification = new Notification();
        notification.recipientId = recipientId;
        notification.createdBy = createdBy;
        notification.category = category;
        notification.type = type;
        notification.title = title;
        notification.body = body;
        notification.data = data;
        notification.severity = attrs.severityOrDefault();
        notification.priority = attrs.priorityOrDefault();
        notification.groupKey = attrs.groupKey();
        notification.targetType = attrs.targetType();
        notification.targetId = attrs.targetId();
        notification.scheduledAt = scheduledAt;
        notification.expiresAt = attrs.expiresAt();
        return notification;
    }

    public boolean isVisibleAt(Instant moment) {
        return deletedAt == null
                && (scheduledAt == null || !scheduledAt.isAfter(moment))
                && (expiresAt == null || expiresAt.isAfter(moment));
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Notification that = (Notification) other;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
