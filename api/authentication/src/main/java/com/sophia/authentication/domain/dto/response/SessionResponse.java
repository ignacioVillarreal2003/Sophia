package com.sophia.authentication.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Active device session")
public record SessionResponse(
        UUID id,
        String ipAddress,
        String userAgent,
        String deviceName,
        boolean rememberMe,
        Instant lastActiveAt,
        Instant createdAt,
        Instant expiresAt,
        @Schema(description = "True when this is the session behind the current request")
        boolean current) {
}
