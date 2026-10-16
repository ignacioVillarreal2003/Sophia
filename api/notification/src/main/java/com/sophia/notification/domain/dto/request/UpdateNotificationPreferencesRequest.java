package com.sophia.notification.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Map;

@Schema(description = "Partial update of the caller's notification preferences (PATCH)")
public record UpdateNotificationPreferencesRequest(
        @Schema(description = "Master switch per channel code (e.g. EMAIL, IN_APP); omitted channels keep their current value")
        @Size(max = 20, message = "At most 20 channels can be updated in one request")
        Map<String, Boolean> channels,

        @Schema(description = "Per-category, per-channel overrides; omitted categories/channels keep their current value")
        @Size(max = 100, message = "At most 100 categories can be updated in one request")
        Map<String, Map<String, Boolean>> categories) {
}
