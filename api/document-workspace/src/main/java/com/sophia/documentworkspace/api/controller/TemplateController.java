package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.TemplateService;
import com.sophia.documentworkspace.domain.dto.request.CreateTemplateRequest;
import com.sophia.documentworkspace.domain.dto.request.InstantiateTemplateRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.TemplateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/templates")
@RequiredArgsConstructor
@Tag(name = "Templates", description = "Reusable, versioned, publishable templates")
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping
    @Operation(summary = "Create a template by snapshotting an existing artifact")
    public ResponseEntity<ApiResponse<TemplateResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateTemplateRequest request) {
        TemplateResponse template = templateService.createFromArtifact(user, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(template));
    }

    @GetMapping
    @Operation(summary = "List the templates of the workspace")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean publishedOnly) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.list(user, workspaceId, publishedOnly)));
    }

    @PostMapping("/{templateId}/refresh")
    @Operation(summary = "Re-snapshot the template from a source artifact (bumps its version)")
    public ResponseEntity<ApiResponse<TemplateResponse>> refresh(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId,
            @RequestParam UUID sourceArtifactId) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.refresh(user, workspaceId, templateId,
                sourceArtifactId)));
    }

    @PostMapping("/{templateId}/publish")
    @Operation(summary = "Publish the template to every workspace member")
    public ResponseEntity<ApiResponse<TemplateResponse>> publish(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.setPublished(user, workspaceId, templateId, true)));
    }

    @PostMapping("/{templateId}/unpublish")
    @Operation(summary = "Unpublish the template")
    public ResponseEntity<ApiResponse<TemplateResponse>> unpublish(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.setPublished(user, workspaceId, templateId, false)));
    }

    @PostMapping("/{templateId}/instantiate")
    @Operation(summary = "Create a new artifact from the template")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> instantiate(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID templateId,
            @Valid @RequestBody InstantiateTemplateRequest request) {
        UUID artifactId = templateService.instantiate(user, workspaceId, templateId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("artifactId", artifactId)));
    }
}
