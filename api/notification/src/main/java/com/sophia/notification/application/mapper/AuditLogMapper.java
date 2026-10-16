package com.sophia.notification.application.mapper;

import com.sophia.notification.domain.dto.response.AuditLogResponse;
import com.sophia.notification.domain.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog entry);
}
