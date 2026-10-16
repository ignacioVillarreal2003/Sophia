package com.sophia.authentication.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Granular permission")
public record PermissionResponse(
        UUID id,
        String name,
        String description,
        String category,
        Instant createdAt,
        Instant updatedAt) {
}
