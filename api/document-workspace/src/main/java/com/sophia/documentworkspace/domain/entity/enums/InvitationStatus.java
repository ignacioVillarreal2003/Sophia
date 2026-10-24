package com.sophia.documentworkspace.domain.entity.enums;

/** Lifecycle of an invitation to join a workspace. */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    /** Withdrawn by an admin, or replaced by a newer invitation once it had expired. */
    REVOKED
}
