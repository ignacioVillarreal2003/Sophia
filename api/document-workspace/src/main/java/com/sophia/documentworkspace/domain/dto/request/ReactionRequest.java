package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Adds or removes an emoji reaction on a comment")
public record ReactionRequest(
        @NotBlank @Size(max = 32)
        String emoji
) {
}
