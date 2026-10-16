package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.ExportService;
import com.sophia.documentworkspace.application.service.ImportService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.entity.enums.ExportFormat;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@RequiredArgsConstructor
@Tag(name = "Export / Import", description = "Content export (MD, HTML, JSON, PDF, DOCX, ZIP) and import (MD, HTML, DOCX, PDF, ZIP/Obsidian/Notion)")
public class ExportImportController {

    private final ExportService exportService;
    private final ImportService importService;

    @GetMapping("/artifacts/{artifactId}/export")
    @Operation(summary = "Export an artifact; folders export their whole subtree as ZIP")
    public ResponseEntity<byte[]> export(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @RequestParam(defaultValue = "MARKDOWN") ExportFormat format) {
        ExportService.ExportResult result = exportService.export(user, workspaceId, artifactId,
                format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.filename())
                        .build()
                        .toString())
                .body(result.content());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = """
            Import content: .md/.html/.txt/.docx/.pdf become block documents; \
            .zip archives (including Obsidian vaults and Notion exports) recreate \
            the folder tree; anything else becomes a file artifact""")
    public ResponseEntity<ApiResponse<Map<String, List<UUID>>>> importContent(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID parentId) {
        List<UUID> created = importService.importFile(user, workspaceId, parentId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("createdArtifactIds", created)));
    }
}
