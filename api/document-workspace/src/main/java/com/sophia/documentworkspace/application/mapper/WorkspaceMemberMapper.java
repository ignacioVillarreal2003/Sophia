package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.WorkspaceMemberResponse;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface WorkspaceMemberMapper {

    @Mapping(target = "workspaceId", source = "workspace.id")
    WorkspaceMemberResponse toResponse(WorkspaceMember member);
}
