package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Reusable template that can be instantiated into new artifacts")
public record TemplateResponse(
        UUID id,
        UUID workspaceId,
        UUID parentId,
        String name,
        String description,
        ArtifactType targetType,
        @Schema(description = "Whether the template is visible to workspace members for instantiation, as opposed to still being drafted")
        boolean published,
        long version,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt
) {
}
