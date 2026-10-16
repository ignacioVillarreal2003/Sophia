package com.sophia.documentworkspace.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Presigned, time-limited download URL for a file artifact")
public record DownloadUrlResponse(
        String url,
        Instant expiresAt
) {
}
