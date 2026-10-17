package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;

import java.time.Instant;
import java.util.UUID;

public record AccessRequestResponse(
        UUID id,
        UUID workspaceId,
        UUID userId,
        String email,
        String message,
        RequestStatus status,
        Instant createdAt,
        Instant decidedAt
) {
}
