package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.TrashService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@RequiredArgsConstructor
@Tag(name = "Trash", description = "Soft delete, restore and permanent deletion")
public class TrashController {

    private final TrashService trashService;

    @DeleteMapping("/artifacts/{artifactId}")
    @Operation(summary = "Move an artifact (and its subtree) to the trash")
    public ResponseEntity<ApiResponse<Void>> trash(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        trashService.trash(user, workspaceId, artifactId);
        return ResponseEntity.ok(ApiResponse.message("Artifact moved to trash"));
    }

    @GetMapping("/trash")
    @Operation(summary = "List the trashed items of the workspace")
    public ResponseEntity<ApiResponse<PageResponse<ArtifactResponse>>> listTrash(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(trashService.listTrash(user, workspaceId, pageable)));
    }

    @PostMapping("/trash/{artifactId}/restore")
    @Operation(summary = "Restore a trashed artifact (and its subtree)")
    public ResponseEntity<ApiResponse<ArtifactResponse>> restore(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(trashService.restore(user, workspaceId, artifactId)));
    }

    @DeleteMapping("/trash/{artifactId}")
    @Operation(summary = "Permanently delete a trashed artifact")
    public ResponseEntity<ApiResponse<Void>> purge(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        trashService.purgeNow(user, workspaceId, artifactId);
        return ResponseEntity.ok(ApiResponse.message("Artifact permanently deleted"));
    }

    @DeleteMapping("/trash")
    @Operation(summary = "Empty the workspace trash")
    public ResponseEntity<ApiResponse<Void>> emptyTrash(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        int purged = trashService.emptyTrash(user, workspaceId);
        return ResponseEntity.ok(ApiResponse.message("Trash emptied (" + purged + " items)"));
    }
}
