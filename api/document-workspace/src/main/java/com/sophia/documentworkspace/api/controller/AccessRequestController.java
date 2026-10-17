package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.AccessRequestService;
import com.sophia.documentworkspace.domain.dto.response.AccessPreviewResponse;
import com.sophia.documentworkspace.domain.dto.response.AccessRequestResponse;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@RequiredArgsConstructor
@Tag(name = "Access requests", description = "Asking the admins of a workspace to be let in")
public class AccessRequestController {

    private final AccessRequestService service;

    public record RequestAccessBody(@Size(max = 500) String message) {
    }

    public record ApproveBody(@NotNull WorkspaceRole role) {
    }

    @GetMapping("/access-request")
    @Operation(summary = "Name of a workspace and the caller's request state (any signed-in user)")
    public ResponseEntity<ApiResponse<AccessPreviewResponse>> preview(@CurrentUser AuthenticatedUser user,
                                                                      @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(service.preview(user, workspaceId)));
    }

    @PostMapping("/access-request")
    @Operation(summary = "Ask the workspace admins for access")
    public ResponseEntity<ApiResponse<AccessRequestResponse>> request(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID workspaceId,
            @Valid @RequestBody(required = false) RequestAccessBody body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.request(user, workspaceId, body == null ? null : body.message())));
    }

    @GetMapping("/access-requests")
    @Operation(summary = "Pending requests to join the workspace (admins)")
    public ResponseEntity<ApiResponse<List<AccessRequestResponse>>> pending(@CurrentUser AuthenticatedUser user,
                                                                            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(service.pending(user, workspaceId)));
    }

    @PostMapping("/access-requests/{requestId}/approve")
    @Operation(summary = "Let the person in with the chosen role (admins)")
    public ResponseEntity<ApiResponse<AccessRequestResponse>> approve(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID workspaceId, @PathVariable UUID requestId,
            @Valid @RequestBody ApproveBody body) {
        return ResponseEntity.ok(ApiResponse.ok(service.approve(user, workspaceId, requestId, body.role())));
    }

    @PostMapping("/access-requests/{requestId}/deny")
    @Operation(summary = "Decline a request (admins)")
    public ResponseEntity<ApiResponse<AccessRequestResponse>> deny(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID workspaceId, @PathVariable UUID requestId) {
        return ResponseEntity.ok(ApiResponse.ok(service.deny(user, workspaceId, requestId)));
    }
}
