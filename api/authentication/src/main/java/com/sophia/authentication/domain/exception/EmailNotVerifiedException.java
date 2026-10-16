package com.sophia.authentication.domain.exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends ApiException {

    public EmailNotVerifiedException() {
        super(ErrorCode.EMAIL_NOT_VERIFIED, HttpStatus.FORBIDDEN, "Email address has not been verified");
    }
}
