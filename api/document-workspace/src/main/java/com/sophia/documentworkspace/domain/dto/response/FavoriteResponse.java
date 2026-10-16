package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A workspace member's favorited (bookmarked) artifact")
public record FavoriteResponse(
        UUID id,
        UUID workspaceId,
        UUID artifactId,
        boolean pinned,
        Instant createdAt,
        ArtifactResponse artifact
) {
}
