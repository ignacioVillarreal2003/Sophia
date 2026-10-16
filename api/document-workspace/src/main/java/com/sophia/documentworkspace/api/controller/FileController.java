package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.FileService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.DownloadUrlResponse;
import com.sophia.documentworkspace.domain.dto.response.FileVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Uploads, downloads, previews and content versions")
public class FileController {

    private final FileService fileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file in one request (small/medium files)")
    public ResponseEntity<ApiResponse<ArtifactResponse>> upload(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID parentId) {
        ArtifactResponse artifact = fileService.upload(user, workspaceId, parentId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(artifact));
    }

    @PutMapping(value = "/{artifactId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a new content version of an existing file")
    public ResponseEntity<ApiResponse<ArtifactResponse>> uploadNewVersion(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(fileService.uploadNewVersion(user, workspaceId, artifactId, file)));
    }

    @GetMapping("/{artifactId}/content")
    @Operation(summary = "Stream the current content through the API (inline by default), for viewers")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> content(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @RequestParam(defaultValue = "false") boolean attachment) {
        var content = fileService.openContent(user, workspaceId, artifactId);
        var disposition = (attachment ? org.springframework.http.ContentDisposition.attachment()
                : org.springframework.http.ContentDisposition.inline())
                .filename(content.filename(), java.nio.charset.StandardCharsets.UTF_8).build();
        var builder = ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(org.springframework.http.MediaType.parseMediaType(content.contentType()));
        if (content.size() != null) {
            builder.contentLength(content.size());
        }
        return builder.body(out -> {
            try (var in = content.stream()) {
                in.transferTo(out);
            }
        });
    }

    @GetMapping("/{artifactId}/download")
    @Operation(summary = "Presigned download URL for the current content")
    public ResponseEntity<ApiResponse<DownloadUrlResponse>> download(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(fileService.downloadUrl(user, workspaceId, artifactId)));
    }

    @GetMapping("/{artifactId}/preview")
    @Operation(summary = "Presigned URL of the generated preview or thumbnail")
    public ResponseEntity<ApiResponse<DownloadUrlResponse>> preview(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @RequestParam(defaultValue = "preview") String kind) {
        return ResponseEntity.ok(ApiResponse.ok(fileService.previewUrl(user, workspaceId, artifactId, kind)));
    }

    @GetMapping("/{artifactId}/versions")
    @Operation(summary = "List the content versions of a file, newest first")
    public ResponseEntity<ApiResponse<List<FileVersionResponse>>> versions(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(fileService.listVersions(user, workspaceId, artifactId)));
    }

    @PostMapping("/{artifactId}/versions/{versionId}/restore")
    @Operation(summary = "Restore a previous content version")
    public ResponseEntity<ApiResponse<ArtifactResponse>> restoreVersion(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.ok(fileService.restoreVersion(user, workspaceId, artifactId, versionId)));
    }
}
