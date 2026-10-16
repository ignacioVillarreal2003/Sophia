package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Result of initiating a multipart upload: presigned URLs for every part. */
@Schema(description = "Result of initiating a multipart upload: presigned URLs for every part")
public record MultipartUploadResponse(
        UUID sessionId,
        List<PartUrl> partUrls,
        long partSizeBytes,
        Instant expiresAt
) {

    public record PartUrl(int partNumber, String url) {
    }
}
