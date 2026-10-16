package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.AdminWorkspaceService;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspaceLimitsRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspaceStatusRequest;
import com.sophia.documentworkspace.domain.dto.response.AdminWorkspaceDetailResponse;
import com.sophia.documentworkspace.domain.dto.response.AdminWorkspaceResponse;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspace administration", description = "Platform-wide status and limits (WORKSPACE_MANAGE)")
public class AdminWorkspaceController {

    private final AdminWorkspaceService adminWorkspaceService;

    @GetMapping
    @Operation(summary = "List every workspace with its status, usage and limits")
    public ResponseEntity<ApiResponse<PageResponse<AdminWorkspaceResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WorkspaceStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminWorkspaceService.list(user, search, status, pageable)));
    }

    @GetMapping("/{workspaceId}")
    @Operation(summary = "Everything about one workspace: usage, members and counts")
    public ResponseEntity<ApiResponse<AdminWorkspaceDetailResponse>> detail(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(adminWorkspaceService.detail(user, workspaceId)));
    }

    @PutMapping("/{workspaceId}/status")
    @Operation(summary = "Set a workspace to ACTIVE, SUSPENDED or READ_ONLY")
    public ResponseEntity<ApiResponse<AdminWorkspaceResponse>> changeStatus(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminWorkspaceService.changeStatus(user, workspaceId, request)));
    }

    @PutMapping("/{workspaceId}/limits")
    @Operation(summary = "Set a workspace's storage quota (null or 0 removes it)")
    public ResponseEntity<ApiResponse<AdminWorkspaceResponse>> changeLimits(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceLimitsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminWorkspaceService.changeLimits(user, workspaceId, request)));
    }
}
