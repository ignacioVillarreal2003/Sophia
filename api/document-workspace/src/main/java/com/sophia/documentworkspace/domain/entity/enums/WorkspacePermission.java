package com.sophia.documentworkspace.domain.entity.enums;

/**
 * Fine-grained actions inside a workspace. Roles are bundles of these
 * permissions; per-artifact ACL entries and MAC labels further restrict them.
 */
public enum WorkspacePermission {

    VIEW_CONTENT,
    CREATE_CONTENT,
    EDIT_CONTENT,
    DELETE_CONTENT,
    COMMENT,
    SHARE_CONTENT,
    EXPORT_CONTENT,
    IMPORT_CONTENT,
    MANAGE_TEMPLATES,
    MANAGE_MEMBERS,
    MANAGE_WORKSPACE,
    MANAGE_SECURITY,
    VIEW_AUDIT
}
