package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Initiates a multipart upload and requests presigned URLs for each part")
public record InitiateMultipartUploadRequest(
        @NotBlank @Size(max = 500)
        String filename,

        @Size(max = 255)
        String mimeType,

        @Min(1)
        Long sizeBytes,

        /** Expected SHA-256 (hex) of the full object; verified on completion. */
        @Schema(description = "Expected SHA-256 (hex) of the full object; verified when the upload is completed")
        @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "checksum must be a SHA-256 hex digest")
        String checksumSha256,

        UUID parentId,

        @Min(1) @Max(10_000)
        Integer expectedParts
) {
}
