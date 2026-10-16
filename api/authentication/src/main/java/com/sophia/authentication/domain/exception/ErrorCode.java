package com.sophia.authentication.domain.exception;

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
    INVALID_CREDENTIALS,
    INVALID_TOKEN,
    ACCOUNT_LOCKED,
    ACCOUNT_DISABLED,
    EMAIL_NOT_VERIFIED,
    OPERATION_NOT_ALLOWED,

    // Rate limiting / upstream
    RATE_LIMITED,
    SERVICE_UNAVAILABLE
}
