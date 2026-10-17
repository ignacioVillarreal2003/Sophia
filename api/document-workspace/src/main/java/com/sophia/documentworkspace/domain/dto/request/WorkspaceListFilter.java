package com.sophia.documentworkspace.domain.dto.request;

import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;

import java.time.Instant;
import java.util.UUID;

/** Everything the workspace list can be narrowed and ordered by. */
public record WorkspaceListFilter(
        String search,
        WorkspaceRole role,
        WorkspaceStatus status,
        Ownership ownership,
        boolean pinnedOnly,
        UUID labelId,
        Instant createdFrom,
        Instant createdTo,
        Instant activeFrom,
        Instant activeTo,
        SortKey sort,
        boolean descending
) {

    public enum Ownership {
        /** Workspaces the caller owns. */
        MINE,
        /** Workspaces somebody else owns and the caller was added to. */
        SHARED
    }

    public enum SortKey {
        NAME,
        CREATED,
        ACTIVITY,
        ACCESSED
    }
}
