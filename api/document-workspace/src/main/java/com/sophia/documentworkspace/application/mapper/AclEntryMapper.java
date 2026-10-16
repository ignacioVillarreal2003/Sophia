package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.AclEntryResponse;
import com.sophia.documentworkspace.domain.entity.AclEntry;
import org.mapstruct.Mapper;

@Mapper
public interface AclEntryMapper {

    AclEntryResponse toResponse(AclEntry entry);
}
