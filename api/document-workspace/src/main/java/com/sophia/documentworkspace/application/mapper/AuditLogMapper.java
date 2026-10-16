package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.AuditLogResponse;
import com.sophia.documentworkspace.domain.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog entry);
}
