package com.sophia.documentworkspace.application.security;

import com.sophia.documentworkspace.application.security.policy.AccessDecision;
import com.sophia.documentworkspace.application.security.policy.AclPolicy;
import com.sophia.documentworkspace.application.security.policy.RolePolicy;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ForbiddenOperationException;
import com.sophia.documentworkspace.domain.security.SecuredResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

/** Access has two sources: the member role (whole workspace) and direct grants on a folder or file. */
@ExtendWith(MockitoExtension.class)
class ResourceAccessEvaluatorTest {

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Mock
    private AclResolver aclResolver;

    private ResourceAccessEvaluator evaluator;

    private record Resource(UUID getId, UUID getWorkspaceId, UUID getOwnerId) implements SecuredResource {
    }

    @BeforeEach
    void setUp() {
        evaluator = new ResourceAccessEvaluator(new RolePolicy(), new AclPolicy(), aclResolver);
        lenient().when(aclResolver.effectiveModes(any(), anyCollection())).thenReturn(Set.of());
    }

    private WorkspaceAccessSnapshot access(WorkspaceRole role, WorkspaceStatus status) {
        return new WorkspaceAccessSnapshot(workspaceId, status, ownerId, UUID.randomUUID(),
                userId, role, MemberStatus.ACTIVE);
    }

    private SecuredResource resource(UUID resourceOwner) {
        return new Resource(UUID.randomUUID(), workspaceId, resourceOwner);
    }

    @Test
    void roleGrantAllows() {
        SecuredResource resource = resource(UUID.randomUUID());
        AccessDecision decision = evaluator.evaluate(access(WorkspaceRole.EDITOR, WorkspaceStatus.ACTIVE),
                resource, AccessMode.WRITE, List.of(resource.getId()));
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void viewerCannotWrite_evenOnItemsTheyCreated() {
        SecuredResource own = resource(userId);
        AccessDecision denied = evaluator.evaluate(access(WorkspaceRole.VIEWER, WorkspaceStatus.ACTIVE),
                own, AccessMode.WRITE, List.of(own.getId()));
        assertThat(denied.denied()).isTrue();
        assertThat(denied.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_ROLE);
    }

    @Test
    void directGrantAllowsWhatTheRoleDenies() {
        SecuredResource resource = resource(UUID.randomUUID());
        lenient().when(aclResolver.effectiveModes(any(), anyCollection())).thenReturn(Set.of(AccessMode.WRITE));

        AccessDecision decision = evaluator.evaluate(access(WorkspaceRole.VIEWER, WorkspaceStatus.ACTIVE),
                resource, AccessMode.WRITE, List.of(resource.getId()));
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void directGrantIsLimitedToTheGrantedModes() {
        SecuredResource resource = resource(UUID.randomUUID());
        lenient().when(aclResolver.effectiveModes(any(), anyCollection())).thenReturn(Set.of(AccessMode.COMMENT));

        AccessDecision decision = evaluator.evaluate(access(WorkspaceRole.VIEWER, WorkspaceStatus.ACTIVE),
                resource, AccessMode.DELETE, List.of(resource.getId()));
        assertThat(decision.denied()).isTrue();
    }

    @Test
    void crossWorkspaceResourceIsDenied() {
        SecuredResource foreign = new Resource(UUID.randomUUID(), UUID.randomUUID(), userId);
        AccessDecision decision = evaluator.evaluate(access(WorkspaceRole.ADMIN, WorkspaceStatus.ACTIVE),
                foreign, AccessMode.READ, List.of(foreign.getId()));
        assertThat(decision.denied()).isTrue();
    }

    @Test
    void readOnlyWorkspaceRejectsMutationsButAllowsReads() {
        SecuredResource resource = resource(userId);
        WorkspaceAccessSnapshot readOnly = access(WorkspaceRole.ADMIN, WorkspaceStatus.READ_ONLY);

        assertThat(evaluator.evaluate(readOnly, resource, AccessMode.READ,
                List.of(resource.getId())).allowed()).isTrue();
        assertThat(evaluator.evaluate(readOnly, resource, AccessMode.WRITE,
                List.of(resource.getId())).denied()).isTrue();
    }

    @Test
    void requireThrowsOnDeny() {
        SecuredResource resource = resource(UUID.randomUUID());
        assertThatThrownBy(() -> evaluator.require(access(WorkspaceRole.VIEWER, WorkspaceStatus.ACTIVE),
                resource, AccessMode.DELETE, List.of(resource.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
