package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Workspace membership record for one user")
public record WorkspaceMemberResponse(
        UUID id,
        UUID workspaceId,
        UUID userId,
        String email,
        String displayName,
        WorkspaceRole role,
        MemberStatus status,
        UUID invitedBy,
        UUID updatedBy,
        Instant joinedAt
) {
}
