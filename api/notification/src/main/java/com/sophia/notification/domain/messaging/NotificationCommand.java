package com.sophia.notification.domain.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationCommand(
        Integer schemaVersion,
        UUID messageId,
        Instant occurredAt,
        Instant scheduledAt,
        String category,
        UUID actorId,
        Attributes attributes,
        Recipient recipient,
        InAppMessage inApp,
        EmailMessage email) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public int schemaVersionOrCurrent() {
        return schemaVersion == null ? CURRENT_SCHEMA_VERSION : schemaVersion;
    }

    public record Recipient(UUID userId, String email, String displayName, String locale) {
    }

    public record InAppMessage(String type, String title, String body, Map<String, String> data) {
    }

    public record EmailMessage(String template, Map<String, String> variables, String subject, String htmlBody) {

        public boolean isTemplated() {
            return template != null && !template.isBlank();
        }
    }

    public record Attributes(String severity, Short priority, String groupKey,
                              String targetType, UUID targetId, Instant expiresAt) {
    }
}
