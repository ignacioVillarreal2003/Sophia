package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.DocumentContentService;
import com.sophia.documentworkspace.domain.dto.request.NameRevisionRequest;
import com.sophia.documentworkspace.domain.dto.request.SaveContentRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.DocumentContentResponse;
import com.sophia.documentworkspace.domain.dto.response.DocumentVersionResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/documents/{documentId}")
@RequiredArgsConstructor
@Tag(name = "Document content", description = "Markdown notes, checklists and spreadsheets: content and version history")
public class DocumentContentController {

    private final DocumentContentService contentService;

    @GetMapping("/content")
    @Operation(summary = "Read the current text content (Markdown or workbook JSON) and its version")
    public ResponseEntity<ApiResponse<DocumentContentResponse>> getContent(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.ok(contentService.getContent(user, workspaceId, documentId)));
    }

    @PutMapping("/content")
    @Operation(summary = "Save the content; answers 409 when it was edited since baseVersion")
    public ResponseEntity<ApiResponse<DocumentContentResponse>> save(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @Valid @RequestBody SaveContentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(contentService.save(user, workspaceId, documentId, request)));
    }

    @GetMapping("/versions")
    @Operation(summary = "List the saved versions of a document, newest first")
    public ResponseEntity<ApiResponse<PageResponse<DocumentVersionResponse>>> versions(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @RequestParam(defaultValue = "false") boolean namedOnly,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                contentService.versions(user, workspaceId, documentId, namedOnly, pageable)));
    }

    @GetMapping("/versions/{versionId}")
    @Operation(summary = "One saved version with its full content")
    public ResponseEntity<ApiResponse<DocumentVersionResponse>> version(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.ok(contentService.version(user, workspaceId, documentId, versionId)));
    }

    @PostMapping("/versions/{versionId}/restore")
    @Operation(summary = "Restore a saved version (recorded as a new version)")
    public ResponseEntity<ApiResponse<DocumentContentResponse>> restore(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.ok(contentService.restore(user, workspaceId, documentId, versionId)));
    }

    @PutMapping("/versions/{versionId}/name")
    @Operation(summary = "Name a version as a checkpoint; named versions are never trimmed")
    public ResponseEntity<ApiResponse<DocumentVersionResponse>> nameVersion(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @PathVariable UUID versionId,
            @Valid @RequestBody NameRevisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                contentService.nameVersion(user, workspaceId, documentId, versionId, request.name())));
    }
}
