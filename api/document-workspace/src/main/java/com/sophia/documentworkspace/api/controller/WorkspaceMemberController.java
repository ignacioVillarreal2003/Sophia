package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.WorkspaceMemberService;
import com.sophia.documentworkspace.domain.dto.request.AddMemberRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateMemberRoleRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceMemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
@RequiredArgsConstructor
@Tag(name = "Workspace members", description = "Membership, roles and suspension")
public class WorkspaceMemberController {

    private final WorkspaceMemberService memberService;

    @GetMapping
    @Operation(summary = "List workspace members")
    public ResponseEntity<ApiResponse<PageResponse<WorkspaceMemberResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.list(user, workspaceId, pageable)));
    }

    @PostMapping
    @Operation(summary = "Add an existing platform user to the workspace (workspace ADMINs only)")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> add(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(memberService.add(user, workspaceId, request)));
    }

    @PutMapping("/{memberId}/role")
    @Operation(summary = "Change a member's role (owner: any; ADMIN: only EDITOR <-> VIEWER)")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> changeRole(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.changeRole(user, workspaceId, memberId, request)));
    }

    @PostMapping("/{memberId}/suspend")
    @Operation(summary = "Suspend a member (owner only)")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> suspend(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.suspend(user, workspaceId, memberId)));
    }

    @PostMapping("/{memberId}/reactivate")
    @Operation(summary = "Reactivate a suspended member (owner only)")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> reactivate(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.reactivate(user, workspaceId, memberId)));
    }

    @DeleteMapping("/{memberId}")
    @Operation(summary = "Remove a member from the workspace")
    public ResponseEntity<ApiResponse<Void>> remove(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId) {
        memberService.remove(user, workspaceId, memberId);
        return ResponseEntity.ok(ApiResponse.message("Member removed"));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Leave the workspace")
    public ResponseEntity<ApiResponse<Void>> leave(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId) {
        memberService.leave(user, workspaceId);
        return ResponseEntity.ok(ApiResponse.message("You left the workspace"));
    }
}
