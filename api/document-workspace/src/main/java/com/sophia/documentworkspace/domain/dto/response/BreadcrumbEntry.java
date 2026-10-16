package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** One step of an artifact's ancestor chain, root first. */
@Schema(description = "One step in an artifact's ancestor path, ordered root first")
public record BreadcrumbEntry(
        UUID id,
        String name,
        ArtifactType type
) {
}
