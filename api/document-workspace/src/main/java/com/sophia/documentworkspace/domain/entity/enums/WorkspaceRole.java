package com.sophia.documentworkspace.domain.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Workspace-scoped roles, each granting a fixed permission bundle.
 * The owner is the member whose id is {@code Workspace.ownerId}; they hold the ADMIN role
 * and additionally pass ownership checks (delete workspace, transfer ownership, member status).
 */
public enum WorkspaceRole {

    ADMIN(EnumSet.allOf(WorkspacePermission.class)),

    EDITOR(EnumSet.of(
            WorkspacePermission.VIEW_CONTENT,
            WorkspacePermission.CREATE_CONTENT,
            WorkspacePermission.EDIT_CONTENT,
            WorkspacePermission.DELETE_CONTENT,
            WorkspacePermission.COMMENT,
            WorkspacePermission.SHARE_CONTENT,
            WorkspacePermission.EXPORT_CONTENT,
            WorkspacePermission.IMPORT_CONTENT,
            WorkspacePermission.MANAGE_TEMPLATES)),

    VIEWER(EnumSet.of(
            WorkspacePermission.VIEW_CONTENT,
            WorkspacePermission.COMMENT,
            WorkspacePermission.EXPORT_CONTENT));

    private final Set<WorkspacePermission> permissions;

    WorkspaceRole(Set<WorkspacePermission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<WorkspacePermission> permissions() {
        return permissions;
    }

    public boolean grants(WorkspacePermission permission) {
        return permissions.contains(permission);
    }
}
