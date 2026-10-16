package com.sophia.documentworkspace.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Base class for every business exception surfaced through the API.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        this(code, status, message, Map.of(), null);
    }

    public ApiException(ErrorCode code, HttpStatus status, String message, Map<String, Object> details) {
        this(code, status, message, details, null);
    }

    public ApiException(ErrorCode code, HttpStatus status, String message,
                        Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }
}
