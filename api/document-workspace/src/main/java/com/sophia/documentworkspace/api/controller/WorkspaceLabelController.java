package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.WorkspaceLabelService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceLabelRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Workspace labels", description = "Personal labels to organize the workspace list")
public class WorkspaceLabelController {

    private static final String COLOR = "^#[0-9A-Fa-f]{6}$";

    private final WorkspaceLabelService labelService;

    public record CreateLabelRequest(@NotBlank @Size(max = 40) String name,
                                     @NotBlank @Pattern(regexp = COLOR) String color) {
    }

    public record UpdateLabelRequest(@Size(min = 1, max = 40) String name,
                                     @Pattern(regexp = COLOR) String color) {
    }

    public record AssignLabelsRequest(@Size(max = 10) List<UUID> labelIds) {
    }

    @GetMapping("/workspace-labels")
    @Operation(summary = "The caller's own labels")
    public ResponseEntity<ApiResponse<List<WorkspaceLabelRef>>> list(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(labelService.list(user)));
    }

    @PostMapping("/workspace-labels")
    @Operation(summary = "Create a personal label")
    public ResponseEntity<ApiResponse<WorkspaceLabelRef>> create(@CurrentUser AuthenticatedUser user,
                                                                 @Valid @RequestBody CreateLabelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(labelService.create(user, request.name(), request.color())));
    }

    @PatchMapping("/workspace-labels/{labelId}")
    @Operation(summary = "Rename or recolor a personal label")
    public ResponseEntity<ApiResponse<WorkspaceLabelRef>> update(@CurrentUser AuthenticatedUser user,
                                                                 @PathVariable UUID labelId,
                                                                 @Valid @RequestBody UpdateLabelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(labelService.update(user, labelId, request.name(), request.color())));
    }

    @DeleteMapping("/workspace-labels/{labelId}")
    @Operation(summary = "Delete a personal label (it disappears from every workspace)")
    public ResponseEntity<ApiResponse<Void>> delete(@CurrentUser AuthenticatedUser user, @PathVariable UUID labelId) {
        labelService.delete(user, labelId);
        return ResponseEntity.ok(ApiResponse.message("Label deleted"));
    }

    @PutMapping("/workspaces/{workspaceId}/labels")
    @Operation(summary = "Set the caller's labels on a workspace")
    public ResponseEntity<ApiResponse<List<WorkspaceLabelRef>>> assign(@CurrentUser AuthenticatedUser user,
                                                                       @PathVariable UUID workspaceId,
                                                                       @Valid @RequestBody AssignLabelsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(labelService.assign(user, workspaceId, request.labelIds())));
    }
}
