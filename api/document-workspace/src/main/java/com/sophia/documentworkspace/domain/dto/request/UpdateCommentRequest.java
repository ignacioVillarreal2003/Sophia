package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Edits an existing comment's body and mentions")
public record UpdateCommentRequest(
        @NotBlank @Size(max = 10_000)
        String body,

        @Size(max = 50)
        List<UUID> mentions
) {
}
