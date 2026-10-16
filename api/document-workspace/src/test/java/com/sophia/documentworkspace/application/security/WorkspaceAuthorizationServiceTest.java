package com.sophia.documentworkspace.application.security;

import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import com.sophia.documentworkspace.domain.exception.ForbiddenOperationException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceAuthorizationServiceTest {

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final AuthenticatedUser user = new AuthenticatedUser(userId, "u@x.com", Set.of(), Set.of());

    @Mock
    private WorkspaceAccessCache accessCache;

    private WorkspaceAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceAuthorizationService(accessCache);
    }

    private WorkspaceAccessSnapshot snapshot(WorkspaceStatus wsStatus, WorkspaceRole role,
                                             MemberStatus memberStatus) {
        return new WorkspaceAccessSnapshot(workspaceId, wsStatus, UUID.randomUUID(),
                role == null ? null : UUID.randomUUID(), userId, role,
                memberStatus);
    }

    @Test
    void unknownWorkspaceYields404() {
        when(accessCache.load(workspaceId, userId)).thenReturn(null);
        assertThatThrownBy(() -> service.requireActiveMember(workspaceId, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nonMembersAreRejected() {
        when(accessCache.load(workspaceId, userId))
                .thenReturn(WorkspaceAccessSnapshot.nonMember(workspaceId, WorkspaceStatus.ACTIVE,
                        UUID.randomUUID(), userId));
        assertThatThrownBy(() -> service.requireActiveMember(workspaceId, user))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void suspendedWorkspaceBlocksRegularUsersButNotWorkspaceManagers() {
        when(accessCache.load(workspaceId, userId))
                .thenReturn(snapshot(WorkspaceStatus.SUSPENDED, WorkspaceRole.ADMIN,
                        MemberStatus.ACTIVE));
        assertThatThrownBy(() -> service.requireActiveMember(workspaceId, user))
                .isInstanceOf(ForbiddenOperationException.class);

        AuthenticatedUser workspaceManager = new AuthenticatedUser(userId, "u@x.com", Set.of(),
                Set.of(AuthenticatedUser.PERMISSION_WORKSPACE_MANAGE));
        when(accessCache.load(workspaceId, userId))
                .thenReturn(snapshot(WorkspaceStatus.SUSPENDED, WorkspaceRole.ADMIN,
                        MemberStatus.ACTIVE));
        assertThat(service.requireActiveMember(workspaceId, workspaceManager)).isNotNull();
    }

    @Test
    void suspendedMembersAreRejected() {
        when(accessCache.load(workspaceId, userId))
                .thenReturn(snapshot(WorkspaceStatus.ACTIVE, WorkspaceRole.EDITOR,
                        MemberStatus.SUSPENDED));
        assertThatThrownBy(() -> service.requireActiveMember(workspaceId, user))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void rolePermissionsAreEnforced() {
        when(accessCache.load(workspaceId, userId))
                .thenReturn(snapshot(WorkspaceStatus.ACTIVE, WorkspaceRole.VIEWER,
                        MemberStatus.ACTIVE));
        assertThat(service.requirePermission(workspaceId, user,
                WorkspacePermission.VIEW_CONTENT)).isNotNull();
        assertThatThrownBy(() -> service.requirePermission(workspaceId, user,
                WorkspacePermission.MANAGE_MEMBERS))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void archivedWorkspacesAllowOnlyReadishPermissions() {
        when(accessCache.load(workspaceId, userId))
                .thenReturn(snapshot(WorkspaceStatus.READ_ONLY, WorkspaceRole.ADMIN,
                        MemberStatus.ACTIVE));
        assertThat(service.requirePermission(workspaceId, user,
                WorkspacePermission.VIEW_CONTENT)).isNotNull();
        assertThatThrownBy(() -> service.requirePermission(workspaceId, user,
                WorkspacePermission.EDIT_CONTENT))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireOwnerChecksTheOwnerId() {
        WorkspaceAccessSnapshot ownerSnapshot = new WorkspaceAccessSnapshot(workspaceId,
                WorkspaceStatus.ACTIVE, userId, UUID.randomUUID(), userId,
                WorkspaceRole.ADMIN, MemberStatus.ACTIVE);
        when(accessCache.load(workspaceId, userId)).thenReturn(ownerSnapshot);
        assertThat(service.requireOwner(workspaceId, user)).isNotNull();

        when(accessCache.load(workspaceId, userId))
                .thenReturn(snapshot(WorkspaceStatus.ACTIVE, WorkspaceRole.ADMIN,
                        MemberStatus.ACTIVE));
        assertThatThrownBy(() -> service.requireOwner(workspaceId, user))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
