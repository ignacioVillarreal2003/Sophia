package com.sophia.notification.domain.dto.response;

import com.sophia.notification.domain.entity.enums.NotificationDispatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "One delivery attempt of a notification through a specific channel")
public record NotificationDispatchResponse(
        UUID id,
        @Schema(description = "Delivery channel code: IN_APP or EMAIL")
        String channel,
        NotificationDispatchStatus status,
        int attempts,
        @Schema(description = "Set only when status is FAILED")
        String lastError,
        Instant sentAt,
        Instant failedAt,
        Instant createdAt) {
}
