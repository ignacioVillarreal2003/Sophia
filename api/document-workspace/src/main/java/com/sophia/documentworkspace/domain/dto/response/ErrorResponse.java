package com.sophia.documentworkspace.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Uniform error payload for every non-2xx response of this API.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldValidationError> fieldErrors,
        Map<String, Object> details
) {

    public record FieldValidationError(String field, String message, Object rejectedValue) {
    }
}
