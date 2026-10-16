package com.sophia.documentworkspace.domain.entity.enums;

/**
 * Concrete action a subject attempts on a single resource (artifact, folder,
 * document). Access modes are what the policy engine evaluates; they are more
 * granular than roles and are the unit ACL entries grant.
 */
public enum AccessMode {

    READ,
    WRITE,
    DELETE,
    COMMENT,
    SHARE,
    MANAGE_PERMISSIONS;

    /** Modes that change the resource and are blocked in archived workspaces. */
    public boolean isMutating() {
        return this != READ;
    }
}
