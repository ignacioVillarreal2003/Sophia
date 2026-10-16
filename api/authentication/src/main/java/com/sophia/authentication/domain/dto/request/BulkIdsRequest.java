package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(description = "A set of resource ids to act on in a single request")
public record BulkIdsRequest(
        @NotEmpty(message = "At least one id is required")
        List<UUID> ids) {
}
