package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Choices about the caller's own workspaces")
public record WorkspacePreferencesResponse(
        int trashRetentionDays,
        @Schema(description = "What applies when nothing was chosen") int defaultTrashRetentionDays,
        boolean customized
) {
}
