package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.WorkspaceInvitationService;
import com.sophia.documentworkspace.domain.dto.request.AddMemberRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.FacetOptionResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceInvitationResponse;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.infrastructure.repository.InvitationSearch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Inviting people to a workspace, and answering the invitations you receive")
public class WorkspaceInvitationController {

    private final WorkspaceInvitationService service;

    @PostMapping("/workspaces/{workspaceId}/invitations")
    @Operation(summary = "Invite an existing platform user to the workspace (admins)")
    public ResponseEntity<ApiResponse<WorkspaceInvitationResponse>> invite(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID workspaceId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.invite(user, workspaceId, request)));
    }

    @GetMapping("/workspaces/{workspaceId}/invitations")
    @Operation(summary = "Invitations of the workspace that nobody has answered yet (admins)")
    public ResponseEntity<ApiResponse<List<WorkspaceInvitationResponse>>> pending(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(service.pendingFor(user, workspaceId)));
    }

    public record ChangeRoleRequest(@jakarta.validation.constraints.NotNull WorkspaceRole role) {
    }

    @PatchMapping("/workspaces/{workspaceId}/invitations/{invitationId}")
    @Operation(summary = "Change the role offered by an invitation that is still open (admins)")
    public ResponseEntity<ApiResponse<WorkspaceInvitationResponse>> changeRole(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID workspaceId, @PathVariable UUID invitationId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.changeRole(user, workspaceId, invitationId, request.role())));
    }

    @DeleteMapping("/workspaces/{workspaceId}/invitations/{invitationId}")
    @Operation(summary = "Withdraw an invitation that is still open (admins)")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID workspaceId, @PathVariable UUID invitationId) {
        service.revoke(user, workspaceId, invitationId);
        return ResponseEntity.ok(ApiResponse.message("Invitation withdrawn"));
    }

    @GetMapping("/invitations")
    @Operation(summary = "Invitations the caller received and has not answered")
    public ResponseEntity<ApiResponse<PageResponse<WorkspaceInvitationResponse>>> mine(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) WorkspaceRole role,
            @RequestParam(required = false) String invitedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "AT") String sort,
            @RequestParam(defaultValue = "true") boolean desc,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        InvitationSearch.Order order = switch (sort.toUpperCase()) {
            case "NAME" -> InvitationSearch.Order.NAME;
            case "EXPIRES" -> InvitationSearch.Order.EXPIRES;
            default -> InvitationSearch.Order.CREATED;
        };
        var query = new WorkspaceInvitationService.Query(q, role, invitedBy, from, to, order, desc);
        return ResponseEntity.ok(ApiResponse.ok(service.mine(user, query, page, size)));
    }

    @GetMapping("/invitations/inviters")
    @Operation(summary = "People who invited the caller, matching the text (to pick one in a filter)")
    public ResponseEntity<ApiResponse<List<FacetOptionResponse>>> inviters(
            @CurrentUser AuthenticatedUser user, @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(service.inviters(user, q)));
    }

    @PostMapping("/invitations/{invitationId}/accept")
    @Operation(summary = "Accept an invitation and become a member")
    public ResponseEntity<ApiResponse<WorkspaceInvitationResponse>> accept(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID invitationId) {
        return ResponseEntity.ok(ApiResponse.ok(service.accept(user, invitationId)));
    }

    @PostMapping("/invitations/{invitationId}/decline")
    @Operation(summary = "Decline an invitation")
    public ResponseEntity<ApiResponse<Void>> decline(
            @CurrentUser AuthenticatedUser user, @PathVariable UUID invitationId) {
        service.decline(user, invitationId);
        return ResponseEntity.ok(ApiResponse.message("Invitation declined"));
    }
}
