package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Workspace summary with the caller's role in it")
public record WorkspaceResponse(
        UUID id,
        String name,
        String description,
        @Schema(description = "Relative URL of the logo image (authenticated GET), or null when none was uploaded")
        String logoUrl,
        String color,
        String language,
        UUID ownerId,
        WorkspaceStatus status,
        @Schema(description = "Days the trash keeps items; null means the system default")
        Integer trashRetentionDays,
        boolean publicSharingEnabled,
        String timezone,
        String dateFormat,
        WorkspaceRole myRole,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "User who last changed the workspace")
        UUID updatedBy,
        @Schema(description = "Set only for soft-deleted workspaces (trash listing)")
        Instant deletedAt
) {
}
