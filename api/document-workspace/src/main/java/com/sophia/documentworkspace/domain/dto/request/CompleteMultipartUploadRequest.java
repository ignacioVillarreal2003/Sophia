package com.sophia.documentworkspace.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

@Schema(description = "Finalizes a multipart upload by supplying the ETag returned for each uploaded part")
public record CompleteMultipartUploadRequest(
        /** partNumber → ETag returned by S3 for each uploaded part. */
        @Schema(description = "Part number to ETag returned by S3 for each uploaded part")
        @NotEmpty
        Map<Integer, String> parts
) {
}
