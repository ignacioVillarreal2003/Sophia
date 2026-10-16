package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Names a document revision as a labeled checkpoint")
public record NameRevisionRequest(
        @NotBlank @Size(max = 120)
        String name
) {
}
