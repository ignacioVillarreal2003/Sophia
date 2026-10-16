package com.sophia.authentication.application.mapper;

import com.sophia.authentication.domain.dto.response.AuditLogResponse;
import com.sophia.authentication.domain.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
