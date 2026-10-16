package com.sophia.documentworkspace.domain.entity;

import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceMemberTest {

    private Workspace workspace() {
        return Workspace.create("ws", null, null, null, "en", UUID.randomUUID());
    }

    @Test
    void ownerIsStoredAsAdmin() {
        WorkspaceMember owner = WorkspaceMember.ownerOf(workspace(), UUID.randomUUID(), "o@x.com", null);
        assertThat(owner.getRole()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(owner.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(owner.getInvitedBy()).isNull();
    }

    @Test
    void joiningRecordsWhoAddedTheMember() {
        UUID admin = UUID.randomUUID();
        WorkspaceMember member = WorkspaceMember.join(workspace(), UUID.randomUUID(), "a@x.com",
                null, WorkspaceRole.EDITOR, admin);
        assertThat(member.getRole()).isEqualTo(WorkspaceRole.EDITOR);
        assertThat(member.getInvitedBy()).isEqualTo(admin);
        assertThat(member.getUpdatedBy()).isEqualTo(admin);
    }

    @Test
    void roleChangeRecordsTheActor() {
        WorkspaceMember member = WorkspaceMember.join(workspace(), UUID.randomUUID(), "a@x.com",
                null, WorkspaceRole.EDITOR, UUID.randomUUID());
        UUID actor = UUID.randomUUID();

        member.assignRole(WorkspaceRole.VIEWER, actor);

        assertThat(member.getRole()).isEqualTo(WorkspaceRole.VIEWER);
        assertThat(member.getUpdatedBy()).isEqualTo(actor);
    }

    @Test
    void suspensionLifecycleRecordsTheActor() {
        WorkspaceMember member = WorkspaceMember.join(workspace(), UUID.randomUUID(), "m@x.com",
                null, WorkspaceRole.EDITOR, UUID.randomUUID());
        UUID owner = UUID.randomUUID();

        member.suspend(owner);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getUpdatedBy()).isEqualTo(owner);

        member.reactivate(owner);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }
}
