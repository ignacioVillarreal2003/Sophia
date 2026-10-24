package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.UserWorkspacePreferencesService;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspacePreferencesRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspacePreferencesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspace-preferences")
@RequiredArgsConstructor
@Tag(name = "Workspace preferences", description = "Choices about the caller's own workspaces")
public class WorkspacePreferencesController {

    private final UserWorkspacePreferencesService preferences;

    @GetMapping
    @Operation(summary = "The caller's workspace preferences")
    public ResponseEntity<ApiResponse<WorkspacePreferencesResponse>> get(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(preferences.get(user)));
    }

    @PutMapping
    @Operation(summary = "Change how long deleted workspaces are kept before they are erased for good")
    public ResponseEntity<ApiResponse<WorkspacePreferencesResponse>> update(
            @CurrentUser AuthenticatedUser user, @Valid @RequestBody UpdateWorkspacePreferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(preferences.update(user, request)));
    }
}
