package com.sophia.authentication.domain.exception;

import org.springframework.http.HttpStatus;

public class OperationNotAllowedException extends ApiException {

    public OperationNotAllowedException(String message) {
        super(ErrorCode.OPERATION_NOT_ALLOWED, HttpStatus.FORBIDDEN, message);
    }
}
