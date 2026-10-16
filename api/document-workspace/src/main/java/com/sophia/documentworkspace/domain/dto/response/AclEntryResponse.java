package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Access granted to one member on one folder or file")
public record AclEntryResponse(
        UUID id,
        UUID workspaceId,
        UUID resourceId,
        UUID subjectUserId,
        Set<AccessMode> modes,
        UUID grantedBy,
        Instant expiresAt,
        Instant createdAt
) {
}
