package com.sophia.authentication.api.controller;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.dto.response.ApiResponse;
import com.sophia.authentication.domain.dto.response.AuditLogResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.application.security.CustomUserDetails;
import com.sophia.authentication.application.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Security audit trail")
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Query audit logs with filters, sorting and pagination")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @Parameter(description = "Filter by user id") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Filter by action, e.g. LOGIN") @RequestParam(required = false) String action,
            @Parameter(description = "Filter by the resource type the action targeted, e.g. User")
            @RequestParam(required = false) String resourceType,
            @Parameter(description = "Filter by the resource the action targeted")
            @RequestParam(required = false) String resourceId,
            @Parameter(description = "Filter by outcome") @RequestParam(required = false) AuditLog.Result result,
            @Parameter(description = "Filter by request id, to trace one request across services")
            @RequestParam(required = false) String requestId,
            @Parameter(description = "ISO-8601 lower bound, e.g. 2026-01-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "ISO-8601 upper bound")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 50, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                auditService.search(userId, action, resourceType, resourceId, result, requestId, from, to, pageable)));
    }

    @GetMapping("/me")
    @Operation(summary = "Query the current user's own audit trail",
            description = "Same filters as the admin search, but scoped to the caller — no "
                    + "special authority required beyond being authenticated.")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> myActivity(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "Filter by action, e.g. LOGIN") @RequestParam(required = false) String action,
            @Parameter(description = "Filter by the resource type the action targeted, e.g. User")
            @RequestParam(required = false) String resourceType,
            @Parameter(description = "Filter by the resource the action targeted")
            @RequestParam(required = false) String resourceId,
            @Parameter(description = "Filter by outcome") @RequestParam(required = false) AuditLog.Result result,
            @Parameter(description = "Filter by request id, to trace one request across services")
            @RequestParam(required = false) String requestId,
            @Parameter(description = "ISO-8601 lower bound, e.g. 2026-01-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "ISO-8601 upper bound")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                auditService.search(principal.getId(), action, resourceType, resourceId, result, requestId,
                        from, to, pageable)));
    }
}
