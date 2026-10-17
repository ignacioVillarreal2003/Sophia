package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.WorkspaceResponse;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface WorkspaceMapper {

    @Mapping(target = "myRole", source = "myRole")
    @Mapping(target = "logoUrl", expression = "java(logoUrl(workspace))")
    @Mapping(target = "purgeAt", ignore = true)
    @Mapping(target = "pinned", ignore = true)
    @Mapping(target = "lastAccessedAt", ignore = true)
    @Mapping(target = "labels", ignore = true)
    @Mapping(target = "stats", ignore = true)
    WorkspaceResponse toResponse(Workspace workspace, WorkspaceRole myRole);

    /** The stored value is a storage key; clients get the authenticated endpoint, versioned by the key's file name. */
    default String logoUrl(Workspace workspace) {
        String key = workspace.getLogoUrl();
        if (key == null || key.isBlank()) {
            return null;
        }
        return "/api/v1/workspaces/" + workspace.getId() + "/logo?v=" + key.substring(key.lastIndexOf('/') + 1);
    }
}
