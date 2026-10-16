package com.sophia.notification.domain.dto.response;

import com.sophia.notification.domain.entity.enums.NotificationSeverity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "A single in-app notification")
public record NotificationResponse(
        UUID id,
        UUID createdBy,
        String category,
        String type,
        NotificationSeverity severity,
        short priority,
        @Schema(description = "Notifications sharing the same group key are collapsed into a single group in list views")
        String groupKey,
        @Schema(description = "Together with targetId, identifies the entity this notification is about "
                + "(e.g. an artifact or workspace); both are null when there is no specific target")
        String targetType,
        @Schema(description = "Id of the entity referenced by targetType")
        UUID targetId,
        String title,
        String body,
        @Schema(description = "Arbitrary key-value payload for client-side rendering (e.g. template variables)")
        Map<String, String> data,
        boolean read,
        @Schema(description = "Null means the notification never expires")
        Instant expiresAt,
        Instant createdAt) {
}
