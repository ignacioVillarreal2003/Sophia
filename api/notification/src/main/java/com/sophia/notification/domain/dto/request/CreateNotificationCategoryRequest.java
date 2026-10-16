package com.sophia.notification.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to create a notification category")
public record CreateNotificationCategoryRequest(
        @Schema(description = "Stable identifier used as the category's code in notifications and preferences")
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must be uppercase letters, digits and underscores, starting with a letter")
        @Size(max = 80)
        String code,
        @NotBlank
        @Size(max = 100)
        String name,
        @Size(max = 255)
        String description,
        @Schema(description = "Whether this category is always delivered, with no per-user opt-out")
        Boolean mandatory) {
}
