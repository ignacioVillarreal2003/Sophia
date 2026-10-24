package com.sophia.ai.domain.exception;

/**
 * Machine-readable error codes returned in every error payload.
 * Clients branch on these, never on the human-readable message.
 */
public enum ErrorCode {

    // Generic
    INTERNAL_ERROR,
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    METHOD_NOT_ALLOWED,
    RESOURCE_NOT_FOUND,
    CONFLICT,

    // Authentication / authorization
    UNAUTHORIZED,
    ACCESS_DENIED,
    OPERATION_NOT_ALLOWED,

    // AI
    AI_DISABLED,
    AI_NOT_CONFIGURED,
    QUOTA_EXCEEDED,
    TOO_MANY_CONCURRENT,
    SOURCE_NOT_SUPPORTED,
    SOURCE_TOO_LARGE,
    SOURCE_UNAVAILABLE,
    GENERATION_REFUSED,
    GENERATION_FAILED,
    UPSTREAM_ERROR,
    UNSUPPORTED_FILE,
    FILE_TOO_LARGE,

    // Rate limiting / upstream
    RATE_LIMITED,
    SERVICE_UNAVAILABLE
}
