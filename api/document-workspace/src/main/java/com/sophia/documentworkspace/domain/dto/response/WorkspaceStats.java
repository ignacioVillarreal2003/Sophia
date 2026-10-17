package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Numbers shown on the detailed workspace cards. */
@Schema(description = "Members, content and storage of a workspace, for the list views")
public record WorkspaceStats(
        long memberCount,
        @Schema(description = "Documents and files (folders are not counted)")
        long itemCount,
        long usedBytes,
        @Schema(description = "Storage quota in MB; null means unlimited")
        Long quotaMb,
        @Schema(description = "A few members, for the avatar stack")
        List<MemberPreview> members
) {
    public record MemberPreview(UUID userId, String displayName, String email) {
    }
}
