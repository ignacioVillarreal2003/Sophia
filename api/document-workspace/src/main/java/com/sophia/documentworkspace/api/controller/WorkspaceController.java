package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.WorkspaceService;
import com.sophia.documentworkspace.domain.dto.request.TransferOwnershipRequest;
import com.sophia.documentworkspace.domain.dto.request.CreateWorkspaceRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspaceSettingsRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspaceRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceUsageResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import java.time.Duration;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspaces", description = "Workspace lifecycle, settings and ownership")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    @Operation(summary = "Create a workspace owned by the caller")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceResponse workspace = workspaceService.create(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(workspace));
    }

    @GetMapping
    @Operation(summary = "List the workspaces the caller belongs to")
    public ResponseEntity<ApiResponse<PageResponse<WorkspaceResponse>>> listMine(
            @CurrentUser AuthenticatedUser user,
            @Parameter(description = "Free-text search over the workspace name") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by the caller's role in the workspace") @RequestParam(required = false) WorkspaceRole role,
            @Parameter(description = "Filter by workspace status") @RequestParam(required = false) WorkspaceStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.listMine(user, search, role, status, pageable)));
    }

    @GetMapping("/trash")
    @Operation(summary = "List the soft-deleted workspaces owned by the caller")
    public ResponseEntity<ApiResponse<PageResponse<WorkspaceResponse>>> listDeleted(
            @CurrentUser AuthenticatedUser user,
            @ParameterObject @PageableDefault(size = 100) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.listDeleted(user, pageable)));
    }

    @GetMapping("/{workspaceId}")
    @Operation(summary = "Get a workspace by id")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> getById(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.getById(user, workspaceId)));
    }

    @PutMapping(value = "/{workspaceId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload (or replace) the workspace logo; PNG, JPEG, WebP or GIF up to 2 MB")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> uploadLogo(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.uploadLogo(user, workspaceId, file)));
    }

    @DeleteMapping("/{workspaceId}/logo")
    @Operation(summary = "Remove the workspace logo")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> deleteLogo(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.deleteLogo(user, workspaceId)));
    }

    @GetMapping("/{workspaceId}/logo")
    @Operation(summary = "Download the workspace logo (members only)")
    public ResponseEntity<byte[]> logo(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        WorkspaceService.LogoContent logo = workspaceService.readLogo(user, workspaceId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable())
                .body(logo.bytes());
    }

    @GetMapping("/{workspaceId}/usage")
    @Operation(summary = "Storage used by the workspace's files against its quota")
    public ResponseEntity<ApiResponse<WorkspaceUsageResponse>> usage(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.usage(user, workspaceId)));
    }

    @PatchMapping("/{workspaceId}")
    @Operation(summary = "Update workspace details (PATCH semantics)")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> update(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.update(user, workspaceId, request)));
    }

    @PatchMapping("/{workspaceId}/settings")
    @Operation(summary = "Update workspace settings (PATCH semantics)")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> updateSettings(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.updateSettings(user, workspaceId, request)));
    }

    @DeleteMapping("/{workspaceId}")
    @Operation(summary = "Soft-delete a workspace (owner only)")
    public ResponseEntity<ApiResponse<Void>> delete(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        workspaceService.delete(user, workspaceId);
        return ResponseEntity.ok(ApiResponse.message("Workspace deleted"));
    }

    @PostMapping("/{workspaceId}/restore")
    @Operation(summary = "Restore a soft-deleted workspace (owner only)")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> restore(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(workspaceService.restore(user, workspaceId)));
    }

    @DeleteMapping("/{workspaceId}/purge")
    @Operation(summary = "Permanently delete a soft-deleted workspace and all its content (owner only)")
    public ResponseEntity<ApiResponse<Void>> purge(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        workspaceService.purge(user, workspaceId);
        return ResponseEntity.ok(ApiResponse.message("Workspace permanently deleted"));
    }

    @PutMapping("/{workspaceId}/owner")
    @Operation(summary = "Transfer workspace ownership to another member (owner only)")
    public ResponseEntity<ApiResponse<Void>> transferOwnership(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody TransferOwnershipRequest request) {
        workspaceService.transferOwnership(user, workspaceId, request);
        return ResponseEntity.ok(ApiResponse.message("Ownership transferred"));
    }

    @PostMapping("/{workspaceId}/suspend")
    @Operation(summary = "Suspend a workspace (platform administrators only)")
    public ResponseEntity<ApiResponse<Void>> suspend(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        workspaceService.suspend(user, workspaceId);
        return ResponseEntity.ok(ApiResponse.message("Workspace suspended"));
    }

    @PostMapping("/{workspaceId}/reactivate")
    @Operation(summary = "Reactivate a suspended workspace (platform administrators only)")
    public ResponseEntity<ApiResponse<Void>> reactivate(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        workspaceService.reactivate(user, workspaceId);
        return ResponseEntity.ok(ApiResponse.message("Workspace reactivated"));
    }
}
