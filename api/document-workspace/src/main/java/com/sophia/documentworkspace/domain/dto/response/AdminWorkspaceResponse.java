package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;

import java.time.Instant;
import java.util.UUID;

/** A workspace as a platform administrator sees it, whether or not they are a member. */
public record AdminWorkspaceResponse(
        UUID id,
        String name,
        String color,
        String logoUrl,
        UUID ownerId,
        String ownerEmail,
        WorkspaceStatus status,
        long memberCount,
        long usedBytes,
        Long storageQuotaMb,
        Instant createdAt,
        Instant updatedAt
) {
}
