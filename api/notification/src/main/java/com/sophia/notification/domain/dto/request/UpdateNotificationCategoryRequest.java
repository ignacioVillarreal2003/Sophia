package com.sophia.notification.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Partial notification category update (PATCH); null fields keep their current value")
public record UpdateNotificationCategoryRequest(
        @Size(max = 100)
        String name,
        @Size(max = 255)
        String description,
        @Schema(description = "Whether this category is always delivered, with no per-user opt-out")
        Boolean mandatory) {
}
