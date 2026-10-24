package com.sophia.ai.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        Map<String, Object> details) {

    public record FieldValidationError(String field, String message, Object rejectedValue) {
    }
}
