package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.AuditService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.AuditLogResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.entity.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Workspace audit trail (VIEW_AUDIT permission required)")
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Query the audit trail with optional filters")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> query(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) AuditLog.Result result,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, name = "q") String text,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(auditService.query(user, workspaceId, action, userId, resourceType,
                resourceId, result, requestId, from, to, text, pageable)));
    }
}
