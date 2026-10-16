package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Comment on an artifact, including any nested replies")
public record CommentResponse(
        UUID id,
        UUID workspaceId,
        UUID artifactId,
        @Schema(description = "Null for document-level comments not anchored to a specific block")
        UUID blockId,
        @Schema(description = "Null for top-level threads")
        UUID parentCommentId,
        UUID authorId,
        String body,
        List<UUID> mentions,
        Map<String, List<String>> reactions,
        boolean edited,
        boolean resolved,
        @Schema(description = "Set only when resolved is true")
        UUID resolvedBy,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> replies
) {
}
