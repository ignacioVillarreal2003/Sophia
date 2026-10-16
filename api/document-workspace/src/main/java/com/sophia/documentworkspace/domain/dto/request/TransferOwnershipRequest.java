package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Transfers workspace ownership to another member")
public record TransferOwnershipRequest(
        @NotNull
        UUID newOwnerUserId
) {
}
