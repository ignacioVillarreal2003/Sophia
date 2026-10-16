package com.sophia.authentication.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Role with its granted permissions")
public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean system,
        Set<String> permissions,
        Instant createdAt,
        Instant updatedAt) {
}
