package com.sophia.documentworkspace.domain.entity.enums;

public enum WorkspaceStatus {

    /** Normal operation; the status every workspace starts in. */
    ACTIVE,

    /** Blocked by a platform administrator; no member can operate on it. */
    SUSPENDED,

    /** Everything stays visible but nothing can be changed. Set by a platform administrator. */
    READ_ONLY
}
