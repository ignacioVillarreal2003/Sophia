package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
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
        Instant deletedAt,
        @Schema(description = "Last time anything inside the workspace changed")
        Instant lastActivityAt,
        @Schema(description = "Trash listing only: when the workspace will be erased for good")
        Instant purgeAt,
        @Schema(description = "Whether the caller pinned this workspace")
        boolean pinned,
        @Schema(description = "When the caller last opened it")
        Instant lastAccessedAt,
        @Schema(description = "The caller's own labels on this workspace")
        List<WorkspaceLabelRef> labels,
        @Schema(description = "Present in the list views only")
        WorkspaceStats stats
) {

    /** Same workspace with the caller-specific and list-only data filled in. */
    public WorkspaceResponse withExtras(boolean pinned, Instant lastAccessedAt, List<WorkspaceLabelRef> labels,
                                        WorkspaceStats stats) {
        return new WorkspaceResponse(id, name, description, logoUrl, color, language, ownerId, status,
                trashRetentionDays, publicSharingEnabled, timezone, dateFormat, myRole, createdAt, updatedAt,
                updatedBy, deletedAt, lastActivityAt, purgeAt, pinned, lastAccessedAt, labels, stats);
    }

    public WorkspaceResponse withPurgeAt(Instant purgeAt) {
        return new WorkspaceResponse(id, name, description, logoUrl, color, language, ownerId, status,
                trashRetentionDays, publicSharingEnabled, timezone, dateFormat, myRole, createdAt, updatedAt,
                updatedBy, deletedAt, lastActivityAt, purgeAt, pinned, lastAccessedAt, labels, stats);
    }
}
