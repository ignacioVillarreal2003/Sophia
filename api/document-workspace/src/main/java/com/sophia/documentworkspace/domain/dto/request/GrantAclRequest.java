package com.sophia.documentworkspace.domain.dto.request;

import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Gives one workspace member access to one folder or file (and what a folder contains). */
@Schema(description = "Grants access modes on one folder or file to a specific workspace member")
public record GrantAclRequest(
        @NotNull
        UUID subjectUserId,

        @NotEmpty
        Set<AccessMode> modes,

        @Future
        Instant expiresAt
) {
}
