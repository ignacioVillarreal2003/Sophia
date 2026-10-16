package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Creates a comment or reply on an artifact")
public record CreateCommentRequest(
        @NotBlank @Size(max = 10_000)
        String body,

        /** Anchors the comment to one block; null for document-level comments. */
        @Schema(description = "Anchors the comment to a specific block; omit for a document-level comment")
        UUID blockId,

        /** Parent comment for replies; null for new threads. */
        @Schema(description = "Parent comment being replied to; omit to start a new thread")
        UUID parentCommentId,

        @Size(max = 50)
        List<UUID> mentions
) {
}
