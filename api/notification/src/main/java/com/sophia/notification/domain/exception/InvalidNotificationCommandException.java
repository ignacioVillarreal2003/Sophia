package com.sophia.notification.domain.exception;

public class InvalidNotificationCommandException extends RuntimeException {

    public InvalidNotificationCommandException(String message) {
        super(message);
    }

    public InvalidNotificationCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
