package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.WorkspaceResponse;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import com.sophia.documentworkspace.support.TestIds;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceMapperTest {

    private final WorkspaceMapper mapper = Mappers.getMapper(WorkspaceMapper.class);

    @Test
    void mapsAWorkspaceWithTheCallersRoleAndItsPreferences() {
        UUID ownerId = UUID.randomUUID();
        Workspace workspace = TestIds.withRandomId(Workspace.create(
                "Engineering", "Backend and platform teams", "workspaces/x/logo/abc.png", "#6366F1",
                "en", ownerId));

        WorkspaceResponse response = mapper.toResponse(workspace, WorkspaceRole.EDITOR);

        assertThat(response.id()).isEqualTo(workspace.getId());
        assertThat(response.name()).isEqualTo("Engineering");
        assertThat(response.description()).isEqualTo("Backend and platform teams");
        assertThat(response.logoUrl()).isEqualTo("/api/v1/workspaces/" + workspace.getId() + "/logo?v=abc.png");
        assertThat(response.color()).isEqualTo("#6366F1");
        assertThat(response.language()).isEqualTo("en");
        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.status()).isEqualTo(WorkspaceStatus.ACTIVE);
        assertThat(response.myRole()).isEqualTo(WorkspaceRole.EDITOR);
        assertThat(response.timezone()).isEqualTo("UTC");
        assertThat(response.dateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(response.publicSharingEnabled()).isFalse();
        assertThat(response.updatedBy()).isEqualTo(ownerId);
        assertThat(response.trashRetentionDays()).isNull();
    }

    @Test
    void preferencesPatchKeepsUnsetFieldsAndZeroClearsRetention() {
        Workspace workspace = TestIds.withRandomId(Workspace.create(
                "Engineering", null, null, null, "en", UUID.randomUUID()));

        workspace.updatePreferences(30, true, "Europe/Madrid", "dd/MM/yyyy");
        assertThat(workspace.getTrashRetentionDays()).isEqualTo(30);
        assertThat(workspace.isPublicSharingEnabled()).isTrue();
        assertThat(workspace.getTimezone()).isEqualTo("Europe/Madrid");
        assertThat(workspace.getDateFormat()).isEqualTo("dd/MM/yyyy");

        workspace.updatePreferences(null, null, null, null);
        assertThat(workspace.getTrashRetentionDays()).isEqualTo(30);
        assertThat(workspace.isPublicSharingEnabled()).isTrue();

        workspace.updatePreferences(0, false, null, null);
        assertThat(workspace.getTrashRetentionDays()).isNull();
        assertThat(workspace.isPublicSharingEnabled()).isFalse();
        assertThat(workspace.getTimezone()).isEqualTo("Europe/Madrid");
    }

    @Test
    void statusStartsActiveAndCanBeChanged() {
        Workspace workspace = TestIds.withRandomId(Workspace.create(
                "Engineering", null, null, null, "en", UUID.randomUUID()));

        assertThat(workspace.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
        workspace.changeStatus(WorkspaceStatus.READ_ONLY);
        assertThat(workspace.getStatus()).isEqualTo(WorkspaceStatus.READ_ONLY);
    }
}
