package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.PublicLinkService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/public-links")
@RequiredArgsConstructor
@Tag(name = "Public links", description = "Read-only links anyone can open without signing in")
public class WorkspacePublicLinksController {

    private final PublicLinkService publicLinkService;

    @GetMapping("/active-count")
    @Operation(summary = "How many public links of the workspace currently work")
    public ResponseEntity<ApiResponse<Map<String, Long>>> activeCount(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", publicLinkService.countActive(user, workspaceId))));
    }

    @DeleteMapping
    @Operation(summary = "Stop sharing everything: revoke every public link of the workspace")
    public ResponseEntity<ApiResponse<Map<String, Long>>> revokeAll(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("revoked", publicLinkService.revokeAll(user, workspaceId))));
    }
}
