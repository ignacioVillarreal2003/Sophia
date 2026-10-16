package com.sophia.authentication.application.mapper;

import com.sophia.authentication.domain.dto.response.PermissionResponse;
import com.sophia.authentication.domain.entity.Permission;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface PermissionMapper {

    PermissionResponse toResponse(Permission permission);

    List<PermissionResponse> toResponseList(List<Permission> permissions);
}
