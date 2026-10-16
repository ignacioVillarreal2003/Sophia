package com.sophia.documentworkspace.application.security;

import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ForbiddenOperationException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Workspace-level authorization rules (workspace state, membership, role permissions).
 */
@Service
@RequiredArgsConstructor
public class WorkspaceAuthorizationService {

    /** Permissions still allowed while a workspace is read-only. */
    private static final Set<WorkspacePermission> READ_ONLY_ALLOWED = Set.of(
            WorkspacePermission.VIEW_CONTENT,
            WorkspacePermission.EXPORT_CONTENT,
            WorkspacePermission.MANAGE_WORKSPACE);

    private final WorkspaceAccessCache accessCache;

    public WorkspaceAccessSnapshot requireActiveMember(UUID workspaceId, AuthenticatedUser user) {
        WorkspaceAccessSnapshot access = accessCache.load(workspaceId, user.id());
        if (access == null) {
            throw new ResourceNotFoundException("Workspace", workspaceId);
        }
        if (access.workspaceStatus() == WorkspaceStatus.SUSPENDED
                && !user.hasPermission(AuthenticatedUser.PERMISSION_WORKSPACE_MANAGE)) {
            throw new ForbiddenOperationException(ErrorCode.WORKSPACE_SUSPENDED,
                    "This workspace is suspended");
        }
        if (!access.isMember()) {
            throw new ForbiddenOperationException(ErrorCode.MEMBERSHIP_REQUIRED,
                    "You are not a member of this workspace");
        }
        if (access.memberStatus() == MemberStatus.SUSPENDED) {
            throw new ForbiddenOperationException(ErrorCode.MEMBER_SUSPENDED,
                    "Your membership in this workspace is suspended");
        }
        return access;
    }

    public WorkspaceAccessSnapshot requirePermission(UUID workspaceId,
                                                     AuthenticatedUser user,
                                                     WorkspacePermission permission) {
        WorkspaceAccessSnapshot access = requireActiveMember(workspaceId, user);
        if (access.workspaceStatus() == WorkspaceStatus.READ_ONLY && !READ_ONLY_ALLOWED.contains(permission)) {
            throw new ForbiddenOperationException(ErrorCode.CONFLICT,
                    "This workspace is read-only");
        }
        if (!access.role().grants(permission)) {
            throw new ForbiddenOperationException(ErrorCode.INSUFFICIENT_ROLE,
                    "Your role does not allow this operation",
                    Map.of("requiredPermission", permission.name(), "role", access.role().name()));
        }
        return access;
    }

    public WorkspaceAccessSnapshot requireOwner(UUID workspaceId, AuthenticatedUser user) {
        WorkspaceAccessSnapshot access = requireActiveMember(workspaceId, user);
        if (!access.isOwner()) {
            throw new ForbiddenOperationException(ErrorCode.ACCESS_DENIED,
                    "Only the workspace owner can perform this operation");
        }
        return access;
    }
}
