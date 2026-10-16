package com.sophia.authentication.infrastructure.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationCommandPayload(
        Integer schemaVersion,
        UUID messageId,
        String source,
        Instant occurredAt,
        Instant scheduledAt,
        String category,
        UUID actorId,
        Attributes attributes,
        Recipient recipient,
        InAppMessage inApp,
        EmailMessage email) {

    public static final int SCHEMA_VERSION = 1;
    public static final String SOURCE = "authentication";
    public static final String CATEGORY_SECURITY = "AUTHENTICATION_SECURITY";

    public static NotificationCommandPayload securityEmail(UUID userId, String recipientEmail, String displayName,
                                                           String locale, String template,
                                                           Map<String, String> variables) {
        return new NotificationCommandPayload(
                SCHEMA_VERSION,
                UUID.randomUUID(),
                SOURCE,
                Instant.now(),
                null,
                CATEGORY_SECURITY,
                null,
                null,
                new Recipient(userId, recipientEmail, displayName, locale),
                null,
                new EmailMessage(template, variables, null, null));
    }

    public record Recipient(UUID userId, String email, String displayName, String locale) {
    }

    public record InAppMessage(String type, String title, String body, Map<String, String> data) {
    }

    public record EmailMessage(String template, Map<String, String> variables, String subject, String htmlBody) {
    }

    public record Attributes(String severity, Short priority, String groupKey,
                             String targetType, UUID targetId, Instant expiresAt) {
    }
}
