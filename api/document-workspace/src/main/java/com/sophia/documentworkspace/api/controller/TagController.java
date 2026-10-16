package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.TagService;
import com.sophia.documentworkspace.domain.dto.request.CreateTagRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateTagRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.dto.response.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags",
        description = "Workspace tags, hierarchy and artifact assignments")
public class TagController {

    private final TagService tagService;

    @PostMapping("/tags")
    @Operation(summary = "Create a tag")
    public ResponseEntity<ApiResponse<TagResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateTagRequest request) {
        TagResponse tag = tagService.create(user, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(tag));
    }

    @GetMapping("/tags")
    @Operation(summary = "List the tags of the workspace")
    public ResponseEntity<ApiResponse<PageResponse<TagResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @ParameterObject @PageableDefault(size = 100, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(tagService.list(user, workspaceId, pageable)));
    }

    @PatchMapping("/tags/{tagId}")
    @Operation(summary = "Update a tag")
    public ResponseEntity<ApiResponse<TagResponse>> update(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID tagId,
            @Valid @RequestBody UpdateTagRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tagService.update(user, workspaceId, tagId, request)));
    }

    @DeleteMapping("/tags/{tagId}")
    @Operation(summary = "Delete a tag and its assignments")
    public ResponseEntity<ApiResponse<Void>> delete(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID tagId) {
        tagService.delete(user, workspaceId, tagId);
        return ResponseEntity.ok(ApiResponse.message("Tag deleted"));
    }

    @PutMapping("/artifacts/{artifactId}/tags/{tagId}")
    @Operation(summary = "Assign a tag to an artifact")
    public ResponseEntity<ApiResponse<Void>> assign(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @PathVariable UUID tagId) {
        tagService.assign(user, workspaceId, tagId, artifactId);
        return ResponseEntity.ok(ApiResponse.message("Tag assigned"));
    }

    @DeleteMapping("/artifacts/{artifactId}/tags/{tagId}")
    @Operation(summary = "Remove a tag from an artifact")
    public ResponseEntity<ApiResponse<Void>> unassign(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @PathVariable UUID tagId) {
        tagService.unassign(user, workspaceId, tagId, artifactId);
        return ResponseEntity.ok(ApiResponse.message("Tag removed"));
    }

    @GetMapping("/artifacts/{artifactId}/tags")
    @Operation(summary = "List the tags of an artifact")
    public ResponseEntity<ApiResponse<List<TagResponse>>> tagsOfArtifact(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(tagService.tagsOfArtifact(user, workspaceId, artifactId)));
    }
}
