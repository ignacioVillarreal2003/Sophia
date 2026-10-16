package com.sophia.authentication.domain.exception;

import org.springframework.http.HttpStatus;

public class AccountDisabledException extends ApiException {

    public AccountDisabledException() {
        super(ErrorCode.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN, "Account is disabled");
    }
}
