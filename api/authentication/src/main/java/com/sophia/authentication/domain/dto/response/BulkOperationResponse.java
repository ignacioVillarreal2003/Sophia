package com.sophia.authentication.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Outcome of an operation applied to many resources at once")
public record BulkOperationResponse(
        @Schema(description = "How many of the requested items succeeded")
        int successCount,

        @Schema(description = "How many failed; each one is detailed in failures")
        int failureCount,

        @Schema(description = "One entry per item that could not be processed")
        List<Failure> failures) {

    @Schema(description = "A single item that could not be processed, and why")
    public record Failure(UUID id, String reason) {
    }

    public static BulkOperationResponse of(int successCount, List<Failure> failures) {
        return new BulkOperationResponse(successCount, failures.size(), failures);
    }
}
