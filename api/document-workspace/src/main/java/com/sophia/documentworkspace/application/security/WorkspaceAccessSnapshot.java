package com.sophia.documentworkspace.application.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;

import java.util.UUID;

/**
 * Cached, immutable view of what one user can do in one workspace.
 * Member fields are null when the user is not a member.
 */
public record WorkspaceAccessSnapshot(
        UUID workspaceId,
        WorkspaceStatus workspaceStatus,
        UUID ownerId,
        UUID memberId,
        UUID userId,
        WorkspaceRole role,
        MemberStatus memberStatus
) {

    public static WorkspaceAccessSnapshot nonMember(UUID workspaceId, WorkspaceStatus status, UUID ownerId,
                                                    UUID userId) {
        return new WorkspaceAccessSnapshot(workspaceId, status, ownerId, null, userId, null, null);
    }

    /** Derived, never serialized: the snapshot is cached in Redis as JSON. */
    @JsonIgnore
    public boolean isMember() {
        return memberId != null;
    }

    /** Derived, never serialized: the snapshot is cached in Redis as JSON. */
    @JsonIgnore
    public boolean isOwner() {
        return ownerId != null && ownerId.equals(userId);
    }
}
