package com.sophia.documentworkspace.application.security;

import com.sophia.documentworkspace.domain.entity.AclEntry;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import com.sophia.documentworkspace.infrastructure.repository.AclEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AclResolverTest {

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();

    @Mock
    private AclEntryRepository repository;

    private AclResolver resolver;
    private WorkspaceAccessSnapshot access;

    @BeforeEach
    void setUp() {
        resolver = new AclResolver(repository);
        access = new WorkspaceAccessSnapshot(workspaceId, WorkspaceStatus.ACTIVE,
                UUID.randomUUID(), UUID.randomUUID(), userId, WorkspaceRole.VIEWER,
                MemberStatus.ACTIVE);
    }

    @Test
    void unionsGrantsAcrossTheAncestorChain() {
        AclEntry userGrant = AclEntry.forUser(workspaceId, resourceId, userId,
                Set.of(AccessMode.WRITE), UUID.randomUUID(), null);
        AclEntry roleGrantOnParent = AclEntry.forUser(workspaceId, parentId, userId,
                Set.of(AccessMode.COMMENT), UUID.randomUUID(), null);
        when(repository.findAllByResourceIdIn(anyCollection()))
                .thenReturn(List.of(userGrant, roleGrantOnParent));

        Set<AccessMode> modes = resolver.effectiveModes(access, List.of(resourceId, parentId));
        assertThat(modes).containsExactlyInAnyOrder(AccessMode.WRITE, AccessMode.COMMENT);
    }

    @Test
    void expiredEntriesAndOtherMembersAreIgnored() {
        AclEntry expired = AclEntry.forUser(workspaceId, resourceId, userId,
                Set.of(AccessMode.DELETE), UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.HOURS));
        AclEntry otherUser = AclEntry.forUser(workspaceId, resourceId, UUID.randomUUID(),
                Set.of(AccessMode.WRITE), UUID.randomUUID(), null);
        when(repository.findAllByResourceIdIn(anyCollection()))
                .thenReturn(List.of(expired, otherUser));

        assertThat(resolver.effectiveModes(access, List.of(resourceId))).isEmpty();
    }

    @Test
    void entriesFromOtherWorkspacesAreIgnored() {
        AclEntry foreign = AclEntry.forUser(UUID.randomUUID(), resourceId, userId,
                Set.of(AccessMode.WRITE), UUID.randomUUID(), null);
        when(repository.findAllByResourceIdIn(anyCollection())).thenReturn(List.of(foreign));

        assertThat(resolver.effectiveModes(access, List.of(resourceId))).isEmpty();
    }

    @Test
    void emptyChainShortCircuits() {
        assertThat(resolver.effectiveModes(access, List.of())).isEmpty();
    }
}
