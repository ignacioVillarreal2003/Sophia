package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.WorkspaceArchiveService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspace archive", description = "Back up a whole workspace as ZIP and restore it as a new workspace")
public class WorkspaceArchiveController {

    private final WorkspaceArchiveService archiveService;

    @GetMapping("/{workspaceId}/archive")
    @Operation(summary = "Download the whole workspace as a ZIP (content only, no members or history)")
    public ResponseEntity<StreamingResponseBody> download(@CurrentUser AuthenticatedUser user,
                                                          @PathVariable UUID workspaceId) {
        String filename = archiveService.exportName(user, workspaceId);
        StreamingResponseBody body = out -> archiveService.write(user, workspaceId, out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    @PostMapping(value = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a new workspace, owned by the caller, from a ZIP produced by the archive download")
    public ResponseEntity<ApiResponse<WorkspaceArchiveService.Restored>> restore(
            @CurrentUser AuthenticatedUser user,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String name) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(archiveService.restore(user, file, name)));
    }
}
