package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;

import java.time.Instant;
import java.util.UUID;

public record StorageRequestResponse(
        UUID id,
        UUID workspaceId,
        String workspaceName,
        UUID requestedBy,
        String requestedByEmail,
        long requestedMb,
        String message,
        RequestStatus status,
        Long grantedMb,
        Instant createdAt,
        Instant handledAt,
        long usedBytes,
        Long quotaMb
) {
}
