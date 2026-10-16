package com.sophia.notification.domain.exception;

/**
 * Machine-readable error codes returned in every error payload.
 * Clients should branch on these, never on the human-readable message.
 */
public enum ErrorCode {

    // Generic
    INTERNAL_ERROR,
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    METHOD_NOT_ALLOWED,
    RESOURCE_NOT_FOUND,
    DUPLICATE_RESOURCE,
    CONFLICT,
    OPTIMISTIC_LOCK_CONFLICT,

    // Authentication / authorization
    UNAUTHORIZED,
    ACCESS_DENIED,
    OPERATION_NOT_ALLOWED,

    // Notification delivery
    INVALID_NOTIFICATION_COMMAND,

    // Rate limiting / upstream
    RATE_LIMITED,
    SERVICE_UNAVAILABLE
}
