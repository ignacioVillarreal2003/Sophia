package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.CollaborationService;
import com.sophia.documentworkspace.application.service.DocumentLockService;
import com.sophia.documentworkspace.application.service.PresenceService;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.domain.dto.realtime.PresenceUser;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/documents/{documentId}/realtime")
@RequiredArgsConstructor
@Tag(name = "Realtime", description = "CRDT initial sync, compaction, presence and locks")
public class RealtimeRestController {

    private final CollaborationService collaborationService;
    private final PresenceService presenceService;
    private final DocumentLockService lockService;
    private final ArtifactAccessService artifactAccess;

    public record CompactRequest(@NotBlank String mergedState) {
    }

    @GetMapping("/updates")
    @Operation(summary = "Full CRDT update log for initial sync (base64, oldest first)")
    public ResponseEntity<ApiResponse<List<String>>> updates(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.ok(collaborationService.loadUpdates(user, workspaceId, documentId)));
    }

    @PostMapping("/compact")
    @Operation(summary = "Replace the stored update log with one merged state update")
    public ResponseEntity<ApiResponse<Void>> compact(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId,
            @Valid @RequestBody CompactRequest request) {
        collaborationService.compact(user, workspaceId, documentId, request.mergedState());
        return ResponseEntity.ok(ApiResponse.message("Update log compacted"));
    }

    @GetMapping("/presence")
    @Operation(summary = "List the users currently collaborating on the document")
    public ResponseEntity<ApiResponse<List<PresenceUser>>> presence(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.READ);
        return ResponseEntity.ok(ApiResponse.ok(presenceService.activeUsers(documentId)));
    }

    @PostMapping("/lock")
    @Operation(summary = "Acquire (or refresh) the exclusive editing lock")
    public ResponseEntity<ApiResponse<DocumentLockService.LockStatus>> acquireLock(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.ok(lockService.acquire(user, workspaceId, documentId)));
    }

    @PostMapping("/lock/extend")
    @Operation(summary = "Extend the exclusive editing lock TTL")
    public ResponseEntity<ApiResponse<DocumentLockService.LockStatus>> extendLock(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.ok(lockService.extend(user, workspaceId, documentId)));
    }

    @DeleteMapping("/lock")
    @Operation(summary = "Release the exclusive editing lock")
    public ResponseEntity<ApiResponse<Void>> releaseLock(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId) {
        lockService.release(user, workspaceId, documentId);
        return ResponseEntity.ok(ApiResponse.message("Lock released"));
    }

    @GetMapping("/lock")
    @Operation(summary = "Current lock status of the document")
    public ResponseEntity<ApiResponse<DocumentLockService.LockStatus>> lockStatus(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.ok(lockService.status(user, workspaceId, documentId)));
    }
}
