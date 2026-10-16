package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Options for a new public link")
public record CreatePublicLinkRequest(
        @Schema(description = "Days until the link stops working; null means it never expires")
        @Min(1) @Max(365)
        Integer expiresInDays
) {
}
