package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.InvitationStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "An invitation to join a workspace, as the invited person and the admins see it")
public record WorkspaceInvitationResponse(
        UUID id,
        UUID workspaceId,
        String workspaceName,
        String workspaceColor,
        String email,
        String displayName,
        WorkspaceRole role,
        String invitedByEmail,
        InvitationStatus status,
        Instant createdAt,
        Instant expiresAt
) {
}
