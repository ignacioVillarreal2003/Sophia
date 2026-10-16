package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.ResourcePermissionService;
import com.sophia.documentworkspace.domain.dto.request.GrantAclRequest;
import com.sophia.documentworkspace.domain.dto.response.AclEntryResponse;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/artifacts/{artifactId}/permissions")
@RequiredArgsConstructor
@Tag(name = "Resource permissions", description = "Per-artifact ACL grants (discretionary layer)")
public class ResourcePermissionController {

    private final ResourcePermissionService permissionService;

    @PostMapping
    @Operation(summary = "Grant access modes on an artifact to a user or role")
    public ResponseEntity<ApiResponse<AclEntryResponse>> grant(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @Valid @RequestBody GrantAclRequest request) {
        AclEntryResponse entry = permissionService.grant(user, workspaceId, artifactId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(entry));
    }

    @GetMapping
    @Operation(summary = "List the ACL entries of an artifact")
    public ResponseEntity<ApiResponse<List<AclEntryResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.list(user, workspaceId, artifactId)));
    }

    @DeleteMapping("/{aclEntryId}")
    @Operation(summary = "Revoke an ACL entry")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @PathVariable UUID aclEntryId) {
        permissionService.revoke(user, workspaceId, artifactId, aclEntryId);
        return ResponseEntity.ok(ApiResponse.message("ACL entry revoked"));
    }
}
