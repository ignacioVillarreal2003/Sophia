package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "One thing that happened in a workspace the caller belongs to")
public record ActivityItem(
        UUID id,
        UUID workspaceId,
        String workspaceName,
        @Schema(description = "Event type such as artifact.created or comment.created")
        String action,
        UUID actorId,
        String actorEmail,
        String actorName,
        @Schema(description = "Kind of the thing it happened to: artifact, comment, workspace...")
        String resourceType,
        String resourceId,
        @Schema(description = "Name of the artifact or workspace involved, when known")
        String resourceName,
        Instant occurredAt
) {
}
