package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Text content (Markdown or workbook JSON) of a document at its current version. */
@Schema(description = "Text content of a document: Markdown for notes/checklists, workbook JSON for spreadsheets")
public record DocumentContentResponse(
        UUID documentId,
        ArtifactType type,
        long version,
        String content,
        Instant updatedAt
) {
}
