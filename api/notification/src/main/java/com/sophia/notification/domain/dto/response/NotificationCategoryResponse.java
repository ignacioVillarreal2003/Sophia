package com.sophia.notification.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Notification category available for subscription and dispatch")
public record NotificationCategoryResponse(
        @Schema(description = "Stable identifier used as the category's code in notifications and preferences")
        String code,
        String name,
        String description,
        @Schema(description = "Whether this category is always delivered, with no per-user opt-out")
        boolean mandatory,
        @Schema(description = "Whether this category is built into the platform and cannot be deleted")
        boolean system,
        UUID createdBy,
        UUID updatedBy) {
}
