package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything a platform administrator sees about one workspace. */
public record AdminWorkspaceDetailResponse(
        AdminWorkspaceResponse workspace,
        String description,
        String language,
        String timezone,
        String dateFormat,
        Integer trashRetentionDays,
        boolean publicSharingEnabled,
        UUID updatedBy,
        Stats stats,
        List<Member> members
) {
    public record Stats(long items, long trashedItems, long activePublicLinks) {
    }

    public record Member(UUID userId, String email, String displayName, WorkspaceRole role,
                         MemberStatus status, Instant joinedAt) {
    }

}
