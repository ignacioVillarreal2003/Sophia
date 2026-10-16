package com.sophia.notification.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
    }

    public BadRequestException(ErrorCode code, String message) {
        super(code, HttpStatus.BAD_REQUEST, message);
    }

    public BadRequestException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, HttpStatus.BAD_REQUEST, message, details);
    }
}
