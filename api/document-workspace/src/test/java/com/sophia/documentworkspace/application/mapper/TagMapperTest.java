package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.TagResponse;
import com.sophia.documentworkspace.domain.entity.Tag;
import com.sophia.documentworkspace.support.TestIds;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TagMapperTest {

    private final TagMapper mapper = Mappers.getMapper(TagMapper.class);

    @Test
    void mapsAllFields() {
        UUID workspaceId = UUID.randomUUID();
        UUID parentTagId = UUID.randomUUID();
        Tag tag = TestIds.withRandomId(Tag.create(workspaceId, "Backend", "#6366F1", parentTagId));

        TagResponse response = mapper.toResponse(tag);

        assertThat(response.id()).isEqualTo(tag.getId());
        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        assertThat(response.name()).isEqualTo("Backend");
        assertThat(response.color()).isEqualTo("#6366F1");
        assertThat(response.parentTagId()).isEqualTo(parentTagId);
    }

    @Test
    void mapsARootTagWithNoParent() {
        Tag tag = TestIds.withRandomId(Tag.create(UUID.randomUUID(), "Frontend", null, null));

        TagResponse response = mapper.toResponse(tag);

        assertThat(response.color()).isNull();
        assertThat(response.parentTagId()).isNull();
    }
}
