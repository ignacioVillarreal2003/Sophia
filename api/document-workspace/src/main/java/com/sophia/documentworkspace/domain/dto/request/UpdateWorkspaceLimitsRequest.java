package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

public record UpdateWorkspaceLimitsRequest(
        @Schema(description = "Storage quota in megabytes; null or 0 removes the limit")
        @Min(0)
        Long storageQuotaMb
) {
}
