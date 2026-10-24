package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.ArtifactService;
import com.sophia.documentworkspace.domain.dto.request.CreateArtifactRequest;
import com.sophia.documentworkspace.domain.dto.request.DuplicateArtifactRequest;
import com.sophia.documentworkspace.domain.dto.request.MoveArtifactRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateArtifactRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.BreadcrumbEntry;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/artifacts")
@RequiredArgsConstructor
@Tag(name = "Artifacts", description = "Hierarchy, CRUD, move, duplicate and MAC labels")
public class ArtifactController {

    private final ArtifactService artifactService;

    @PostMapping
    @Operation(summary = "Create an artifact (folders, documents, links, embeds, snippets)")
    public ResponseEntity<ApiResponse<ArtifactResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateArtifactRequest request) {
        ArtifactResponse artifact = artifactService.create(user, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(artifact));
    }

    @GetMapping("/{artifactId}")
    @Operation(summary = "Get an artifact by id")
    public ResponseEntity<ApiResponse<ArtifactResponse>> getById(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.getById(user, workspaceId, artifactId)));
    }

    @GetMapping
    @Operation(summary = "List children of a folder, or the workspace root when parentId is omitted")
    public ResponseEntity<ApiResponse<PageResponse<ArtifactResponse>>> listChildren(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID parentId,
            @ParameterObject @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.listChildren(user, workspaceId, parentId, pageable)));
    }

    @GetMapping("/documents")
    @Operation(summary = "Find documents (notes, checklists, spreadsheets) by name, most recently edited first")
    public ResponseEntity<ApiResponse<List<ArtifactResponse>>> findDocuments(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean studySources) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.findDocuments(user, workspaceId, q, limit, studySources)));
    }

    @GetMapping("/{artifactId}/breadcrumb")
    @Operation(summary = "Ancestor chain of an artifact, root first")
    public ResponseEntity<ApiResponse<List<BreadcrumbEntry>>> breadcrumb(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.breadcrumb(user, workspaceId, artifactId)));
    }

    @PatchMapping("/{artifactId}")
    @Operation(summary = "Update an artifact (PATCH semantics, type-specific fields included)")
    public ResponseEntity<ApiResponse<ArtifactResponse>> update(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @Valid @RequestBody UpdateArtifactRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.update(user, workspaceId, artifactId, request)));
    }

    @PutMapping("/{artifactId}/parent")
    @Operation(summary = "Move an artifact under a new parent folder (null = workspace root)")
    public ResponseEntity<ApiResponse<ArtifactResponse>> move(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @Valid @RequestBody MoveArtifactRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(artifactService.move(user, workspaceId, artifactId, request)));
    }

    @PostMapping("/{artifactId}/duplicate")
    @Operation(summary = "Duplicate an artifact; folders are copied recursively")
    public ResponseEntity<ApiResponse<ArtifactResponse>> duplicate(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @Valid @RequestBody(required = false) DuplicateArtifactRequest request) {
        DuplicateArtifactRequest effective = request == null
                ? new DuplicateArtifactRequest(null, null)
                : request;
        ArtifactResponse copy = artifactService.duplicate(user, workspaceId, artifactId, effective);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(copy));
    }

}
