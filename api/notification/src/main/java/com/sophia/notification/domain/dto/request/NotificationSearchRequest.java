package com.sophia.notification.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Schema(description = "Filter and search criteria for listing the caller's notifications")
public record NotificationSearchRequest(
        @Schema(description = "Absent or null is treated as false (all notifications, not just unread)")
        Boolean unreadOnly,

        @Size(max = 80) String category,

        @Size(max = 40) String type,

        @Schema(description = "INFO, SUCCESS, WARNING or ERROR")
        @Size(max = 20) String severity,

        @Size(max = 200) String groupKey,

        @Schema(description = "Inclusive lower bound on createdAt")
        Instant from,

        @Schema(description = "Inclusive upper bound on createdAt")
        Instant to,

        @Schema(description = "Case-insensitive match against the title and body")
        @Size(max = 200) String search) {

    public NotificationSearchRequest {
        // Every filter is optional: an absent parameter must not fail to bind.
        unreadOnly = unreadOnly != null && unreadOnly;
        category = trimToNull(category);
        type = trimToNull(type);
        severity = trimToNull(severity);
        groupKey = trimToNull(groupKey);
        search = trimToNull(search);
    }

    public String searchPattern() {
        return search == null ? null : "%" + search.toLowerCase() + "%";
    }

    public String normalizedSeverity() {
        return severity == null ? null : severity.toUpperCase();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
