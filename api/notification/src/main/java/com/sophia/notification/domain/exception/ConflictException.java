package com.sophia.notification.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }

    public ConflictException(ErrorCode code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }

    public ConflictException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, HttpStatus.CONFLICT, message, details);
    }
}
