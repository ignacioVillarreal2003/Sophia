package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.MultipartUploadService;
import com.sophia.documentworkspace.domain.dto.request.CompleteMultipartUploadRequest;
import com.sophia.documentworkspace.domain.dto.request.InitiateMultipartUploadRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.MultipartUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/uploads/multipart")
@RequiredArgsConstructor
@Tag(name = "Multipart uploads", description = "Large-file uploads via presigned part URLs")
public class MultipartUploadController {

    private final MultipartUploadService multipartUploadService;

    @PostMapping
    @Operation(summary = "Initiate a multipart upload and receive presigned part URLs")
    public ResponseEntity<ApiResponse<MultipartUploadResponse>> initiate(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody InitiateMultipartUploadRequest request) {
        MultipartUploadResponse response = multipartUploadService.initiate(user, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/{sessionId}/complete")
    @Operation(summary = "Complete the upload: verify size/checksum, scan, create the artifact")
    public ResponseEntity<ApiResponse<ArtifactResponse>> complete(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody CompleteMultipartUploadRequest request) {
        ArtifactResponse artifact = multipartUploadService.complete(user, workspaceId, sessionId,
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(artifact));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Abort an in-progress multipart upload")
    public ResponseEntity<ApiResponse<Void>> abort(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID sessionId) {
        multipartUploadService.abort(user, workspaceId, sessionId);
        return ResponseEntity.ok(ApiResponse.message("Upload aborted"));
    }
}
