package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.WorkspaceMemberResponse;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.support.TestIds;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceMemberMapperTest {

    private final WorkspaceMemberMapper mapper = Mappers.getMapper(WorkspaceMemberMapper.class);

    private Workspace workspace() {
        return TestIds.withRandomId(Workspace.create(
                "Engineering", null, null, null, "en", UUID.randomUUID()));
    }

    @Test
    void mapsAnOwnerAndFlattensTheWorkspaceIdFromTheAssociation() {
        Workspace workspace = workspace();
        UUID userId = UUID.randomUUID();
        WorkspaceMember member = TestIds.withRandomId(
                WorkspaceMember.ownerOf(workspace, userId, "ada@sophia.local", "Ada Lovelace"));

        WorkspaceMemberResponse response = mapper.toResponse(member);

        assertThat(response.id()).isEqualTo(member.getId());
        assertThat(response.workspaceId()).isEqualTo(workspace.getId());
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("ada@sophia.local");
        assertThat(response.displayName()).isEqualTo("Ada Lovelace");
        assertThat(response.role()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.invitedBy()).isNull();
        assertThat(response.joinedAt()).isEqualTo(member.getJoinedAt());
    }

    @Test
    void mapsAnAddedEditor() {
        Workspace workspace = workspace();
        UUID invitedBy = UUID.randomUUID();
        WorkspaceMember member = TestIds.withRandomId(WorkspaceMember.join(
                workspace, UUID.randomUUID(), "grace@sophia.local", "Grace Hopper",
                WorkspaceRole.EDITOR, invitedBy));

        WorkspaceMemberResponse response = mapper.toResponse(member);

        assertThat(response.role()).isEqualTo(WorkspaceRole.EDITOR);
        assertThat(response.invitedBy()).isEqualTo(invitedBy);
    }
}
