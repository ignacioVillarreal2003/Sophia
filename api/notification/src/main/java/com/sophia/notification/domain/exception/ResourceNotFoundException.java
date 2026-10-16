package com.sophia.notification.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "%s not found: %s".formatted(resource, id),
                Map.of("resource", resource, "id", String.valueOf(id)));
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
