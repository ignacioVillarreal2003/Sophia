package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Saves the full text content of a document, guarded by the version it was based on")
public record SaveContentRequest(
        @NotNull @Size(max = 8_000_000)
        String content,

        @Schema(description = "Content version the edit started from; a mismatch answers 409")
        @PositiveOrZero
        long baseVersion
) {
}
