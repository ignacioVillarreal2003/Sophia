package com.sophia.authentication.domain.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends ApiException {

    public AccountLockedException(String message) {
        super(ErrorCode.ACCOUNT_LOCKED, HttpStatus.LOCKED, message);
    }
}
