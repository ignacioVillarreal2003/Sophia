package com.sophia.ai.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String message) {
        super(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, message);
    }

    public ForbiddenOperationException(ErrorCode code, String message) {
        super(code, HttpStatus.FORBIDDEN, message);
    }

    public ForbiddenOperationException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, HttpStatus.FORBIDDEN, message, details);
    }
}
