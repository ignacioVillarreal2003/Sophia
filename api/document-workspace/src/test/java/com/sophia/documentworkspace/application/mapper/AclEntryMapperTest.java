package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.AclEntryResponse;
import com.sophia.documentworkspace.domain.entity.AclEntry;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.support.TestIds;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AclEntryMapperTest {

    private final AclEntryMapper mapper = Mappers.getMapper(AclEntryMapper.class);

    @Test
    void mapsAUserScopedEntry() {
        UUID workspaceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID subjectUserId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        AclEntry entry = TestIds.withRandomId(AclEntry.forUser(
                workspaceId, resourceId, subjectUserId, Set.of(AccessMode.READ), grantedBy, expiresAt));

        AclEntryResponse response = mapper.toResponse(entry);

        assertThat(response.id()).isEqualTo(entry.getId());
        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        assertThat(response.resourceId()).isEqualTo(resourceId);
        assertThat(response.subjectUserId()).isEqualTo(subjectUserId);
        assertThat(response.modes()).containsExactly(AccessMode.READ);
        assertThat(response.grantedBy()).isEqualTo(grantedBy);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

}
