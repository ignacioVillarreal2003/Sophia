package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.PublicLinkService;
import com.sophia.documentworkspace.domain.dto.request.CreatePublicLinkRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.PublicLinkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/artifacts/{artifactId}/public-links")
@RequiredArgsConstructor
@Tag(name = "Public links", description = "Read-only links anyone can open without signing in")
public class PublicLinkController {

    private final PublicLinkService publicLinkService;

    @PostMapping
    @Operation(summary = "Create a public link (the token is returned only in this response)")
    public ResponseEntity<ApiResponse<PublicLinkResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @Valid @RequestBody(required = false) CreatePublicLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(publicLinkService.create(user, workspaceId, artifactId, request)));
    }

    @GetMapping
    @Operation(summary = "List the active public links of an artifact (without their tokens)")
    public ResponseEntity<ApiResponse<List<PublicLinkResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(publicLinkService.list(user, workspaceId, artifactId)));
    }

    @DeleteMapping("/{linkId}")
    @Operation(summary = "Revoke a public link")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @PathVariable UUID linkId) {
        publicLinkService.revoke(user, workspaceId, artifactId, linkId);
        return ResponseEntity.ok(ApiResponse.message("Public link revoked"));
    }
}
