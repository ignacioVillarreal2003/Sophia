package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Tag that can be applied to artifacts within a workspace")
public record TagResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String color,
        UUID parentTagId,
        Instant createdAt
) {
}
