package com.sophia.documentworkspace.application.security.policy;

import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Discretionary RBAC policy: maps each {@link AccessMode} to the workspace
 * permission the subject's role must grant.
 */
@Component
public class RolePolicy implements ResourcePolicy {

    private static final Map<AccessMode, WorkspacePermission> MODE_TO_PERMISSION = Map.of(
            AccessMode.READ, WorkspacePermission.VIEW_CONTENT,
            AccessMode.WRITE, WorkspacePermission.EDIT_CONTENT,
            AccessMode.DELETE, WorkspacePermission.DELETE_CONTENT,
            AccessMode.COMMENT, WorkspacePermission.COMMENT,
            AccessMode.SHARE, WorkspacePermission.SHARE_CONTENT,
            AccessMode.MANAGE_PERMISSIONS, WorkspacePermission.MANAGE_SECURITY);

    @Override
    public AccessDecision evaluate(PolicyContext context) {
        WorkspacePermission required = MODE_TO_PERMISSION.get(context.mode());
        if (required == null) {
            return AccessDecision.deny(ErrorCode.ACCESS_DENIED,
                    "No permission mapping for access mode " + context.mode());
        }
        if (context.access().role().grants(required)) {
            return AccessDecision.allow();
        }
        return AccessDecision.deny(ErrorCode.INSUFFICIENT_ROLE,
                "Role %s does not grant %s".formatted(context.access().role(), required));
    }
}
