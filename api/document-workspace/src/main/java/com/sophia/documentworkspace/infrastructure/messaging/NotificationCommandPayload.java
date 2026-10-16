package com.sophia.documentworkspace.infrastructure.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationCommandPayload(
        UUID messageId,
        Instant occurredAt,
        String category,
        UUID actorId,
        Attributes attributes,
        Recipient recipient,
        InAppMessage inApp,
        EmailMessage email) {

    public static final String CATEGORY_PREFIX = "DOCUMENT_WORKSPACE_";
    public static final String TARGET_ARTIFACT = "ARTIFACT";
    public static final String TARGET_WORKSPACE = "WORKSPACE";

    public static NotificationCommandPayload inApp(String category, UUID recipientId, UUID actorId, String type,
                                                   String title, String body, Map<String, String> data,
                                                   String targetType, UUID targetId) {
        return new NotificationCommandPayload(UUID.randomUUID(), Instant.now(), category, actorId,
                Attributes.target(targetType, targetId),
                new Recipient(recipientId, null, null),
                new InAppMessage(type, title, body, data),
                null);
    }

    public static NotificationCommandPayload inAppWithEmail(String category, UUID recipientId, UUID actorId,
                                                            String type, String title, String body,
                                                            Map<String, String> data,
                                                            String targetType, UUID targetId) {
        return new NotificationCommandPayload(UUID.randomUUID(), Instant.now(), category, actorId,
                Attributes.target(targetType, targetId),
                new Recipient(recipientId, null, null),
                new InAppMessage(type, title, body, data),
                new EmailMessage("generic.notification",
                        Map.of("title", title, "body", body == null ? "" : body), null, null));
    }

    public static NotificationCommandPayload email(String category, String recipientEmail, UUID actorId,
                                                   String template, Map<String, String> variables) {
        return new NotificationCommandPayload(UUID.randomUUID(), Instant.now(), category, actorId,
                null,
                new Recipient(null, recipientEmail, null),
                null,
                new EmailMessage(template, variables, null, null));
    }

    public String partitionKey() {
        return recipient.userId() != null ? recipient.userId().toString() : recipient.email();
    }

    public record Recipient(UUID userId, String email, String displayName) {
    }

    public record InAppMessage(String type, String title, String body, Map<String, String> data) {
    }

    public record EmailMessage(String template, Map<String, String> variables, String subject, String htmlBody) {
    }

    public record Attributes(String severity, Short priority, String groupKey,
                              String targetType, UUID targetId, Instant expiresAt) {

        public static Attributes target(String targetType, UUID targetId) {
            if (targetType == null || targetId == null) {
                return null;
            }
            return new Attributes(null, null, null, targetType, targetId, null);
        }
    }
}
