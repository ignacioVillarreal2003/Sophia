package com.sophia.documentworkspace.domain.dto.response;

import com.sophia.documentworkspace.api.filter.CorrelationIdFilter;
import org.slf4j.MDC;

import java.time.Instant;

/** Uniform envelope for every successful API response. */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp,
        String requestId) {

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now(),
                MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY));
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, null, message, Instant.now(),
                MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY));
    }
}
