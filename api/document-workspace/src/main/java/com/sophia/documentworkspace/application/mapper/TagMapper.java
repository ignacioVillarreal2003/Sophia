package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.TagResponse;
import com.sophia.documentworkspace.domain.entity.Tag;
import org.mapstruct.Mapper;

@Mapper
public interface TagMapper {

    TagResponse toResponse(Tag tag);
}
