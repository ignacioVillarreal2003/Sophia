package com.sophia.notification.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Caller's effective notification preferences, combining channel switches and category overrides")
public record NotificationPreferencesResponse(
        @Schema(description = "Master switch per channel code (e.g. EMAIL, IN_APP)")
        Map<String, Boolean> channels,
        @Schema(description = "Per-category, per-channel overrides layered on top of the channel master switches")
        Map<String, Map<String, Boolean>> categories) {
}
