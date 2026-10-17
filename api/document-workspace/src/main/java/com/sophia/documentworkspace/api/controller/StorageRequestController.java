package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.StorageRequestService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.dto.response.StorageRequestResponse;
import com.sophia.documentworkspace.domain.entity.enums.RequestStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Storage requests", description = "Workspace owners ask for more storage; platform admins answer")
public class StorageRequestController {

    private final StorageRequestService service;

    public record CreateStorageRequest(@Min(1) @Max(1_048_576) long requestedMb, @Size(max = 500) String message) {
    }

    public record ApproveStorageRequest(@Min(1) @Max(1_048_576) Long grantedMb) {
    }

    @PostMapping("/workspaces/{workspaceId}/storage-request")
    @Operation(summary = "Owner asks the platform administrators for more storage")
    public ResponseEntity<ApiResponse<StorageRequestResponse>> request(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateStorageRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                service.request(user, workspaceId, body.requestedMb(), body.message())));
    }

    @GetMapping("/workspaces/{workspaceId}/storage-request")
    @Operation(summary = "Latest storage request of the workspace (owner); data is null when there is none")
    public ResponseEntity<ApiResponse<StorageRequestResponse>> latest(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(service.latest(user, workspaceId)));
    }

    @GetMapping("/admin/storage-requests")
    @Operation(summary = "Storage requests by status, oldest first (WORKSPACE_MANAGE)")
    public ResponseEntity<ApiResponse<PageResponse<StorageRequestResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(required = false) RequestStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(user, status, pageable)));
    }

    @GetMapping("/admin/storage-requests/pending-count")
    @Operation(summary = "How many storage requests wait for an answer (WORKSPACE_MANAGE)")
    public ResponseEntity<ApiResponse<Map<String, Long>>> pendingCount(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", service.pendingCount(user))));
    }

    @PostMapping("/admin/storage-requests/{requestId}/approve")
    @Operation(summary = "Add storage to the workspace quota (defaults to what was asked)")
    public ResponseEntity<ApiResponse<StorageRequestResponse>> approve(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) ApproveStorageRequest body) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.approve(user, requestId, body == null ? null : body.grantedMb())));
    }

    @PostMapping("/admin/storage-requests/{requestId}/deny")
    @Operation(summary = "Decline a storage request")
    public ResponseEntity<ApiResponse<StorageRequestResponse>> deny(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID requestId) {
        return ResponseEntity.ok(ApiResponse.ok(service.deny(user, requestId)));
    }
}
