package com.sophia.notification.domain.entity;

import com.sophia.notification.domain.entity.enums.NotificationSeverity;

import java.time.Instant;
import java.util.UUID;

public record NotificationAttributes(
        NotificationSeverity severity,
        Short priority,
        String groupKey,
        String targetType,
        UUID targetId,
        Instant expiresAt) {

    private static final NotificationSeverity DEFAULT_SEVERITY = NotificationSeverity.INFO;
    private static final short DEFAULT_PRIORITY = 0;

    public static NotificationAttributes defaults() {
        return new NotificationAttributes(DEFAULT_SEVERITY, DEFAULT_PRIORITY, null, null, null, null);
    }

    public NotificationSeverity severityOrDefault() {
        return severity == null ? DEFAULT_SEVERITY : severity;
    }

    public short priorityOrDefault() {
        return priority == null ? DEFAULT_PRIORITY : priority;
    }
}
