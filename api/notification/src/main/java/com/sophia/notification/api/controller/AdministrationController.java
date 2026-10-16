package com.sophia.notification.api.controller;

import com.sophia.notification.api.security.CurrentUser;
import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.application.service.AuditService;
import com.sophia.notification.application.service.DeadLetterService;
import com.sophia.notification.domain.dto.response.ApiResponse;
import com.sophia.notification.domain.dto.response.AuditLogResponse;
import com.sophia.notification.domain.dto.response.DeadLetterReplayResponse;
import com.sophia.notification.domain.dto.response.PageResponse;
import com.sophia.notification.domain.entity.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Audit trail and dead letter recovery")
public class AdministrationController {

    private final AuditService auditService;
    private final DeadLetterService deadLetterService;

    @GetMapping("/audit-log")
    @PreAuthorize("hasAuthority('NOTIFICATION_AUDIT_READ')")
    @Operation(summary = "Search the audit trail of privileged actions")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> auditLog(
            @Parameter(description = "Who performed the action") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Action name") @RequestParam(required = false) String action,
            @Parameter(description = "Resource type the action targeted, e.g. NotificationCategory")
            @RequestParam(required = false) String resourceType,
            @Parameter(description = "Resource the action targeted, e.g. a category code or a user id")
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) AuditLog.Result result,
            @Parameter(description = "Filter by request id, to trace one request across services")
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                auditService.search(userId, action, resourceType, resourceId, result, requestId, from, to, pageable)));
    }

    @PostMapping("/dead-letters/replay")
    @PreAuthorize("hasAuthority('NOTIFICATION_DEAD_LETTER_REPLAY')")
    @Operation(summary = "Republish records from a dead letter topic back onto the topic they came from",
            description = "Replays at most 'max' records per call so recovery can be done in controlled steps.")
    public ResponseEntity<ApiResponse<DeadLetterReplayResponse>> replay(
            @CurrentUser AuthenticatedUser actor,
            @Parameter(description = "Dead letter topic owned by this service") @RequestParam String topic,
            @RequestParam(defaultValue = "100") int max) {
        return ResponseEntity.ok(ApiResponse.ok(deadLetterService.replay(topic, max, actor)));
    }
}
