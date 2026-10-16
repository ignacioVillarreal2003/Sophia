package com.sophia.documentworkspace.domain.exception;

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

    // Rate limiting / upstream
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,

    // Authentication / authorization
    UNAUTHORIZED,
    ACCESS_DENIED,
    MEMBERSHIP_REQUIRED,
    INSUFFICIENT_ROLE,

    // Workspace / membership lifecycle
    WORKSPACE_SUSPENDED,
    MEMBER_SUSPENDED,
    OWNERSHIP_TRANSFER_REQUIRED,

    // Artifacts and hierarchy
    UNSUPPORTED_ARTIFACT_TYPE,
    INVALID_HIERARCHY,
    ARTIFACT_LOCKED,
    QUOTA_EXCEEDED,

    // Files
    UPLOAD_FAILED,
    UPLOAD_NOT_FOUND,
    FILE_TOO_LARGE,
    CHECKSUM_MISMATCH,
    VIRUS_DETECTED,
    PREVIEW_UNAVAILABLE,

    // Search
    SEARCH_UNAVAILABLE,

    // Import / export
    EXPORT_FAILED,
    IMPORT_FAILED,

    // Realtime
    REALTIME_ERROR
}
