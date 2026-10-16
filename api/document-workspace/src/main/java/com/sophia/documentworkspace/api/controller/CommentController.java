package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.CommentService;
import com.sophia.documentworkspace.domain.dto.request.CreateCommentRequest;
import com.sophia.documentworkspace.domain.dto.request.ReactionRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateCommentRequest;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.CommentResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Threaded comments, mentions, reactions and resolution")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/artifacts/{artifactId}/comments")
    @Operation(summary = "Comment on an artifact (optionally on one block, or as a reply)")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse comment = commentService.create(user, workspaceId, artifactId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(comment));
    }

    @GetMapping("/artifacts/{artifactId}/comments")
    @Operation(summary = "List comment threads of an artifact (replies embedded)")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @RequestParam(required = false) UUID blockId,
            @RequestParam(defaultValue = "true") boolean includeResolved,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.listThreads(user, workspaceId, artifactId, blockId,
                includeResolved, pageable)));
    }

    @PatchMapping("/comments/{commentId}")
    @Operation(summary = "Edit a comment (author only)")
    public ResponseEntity<ApiResponse<CommentResponse>> edit(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.edit(user, workspaceId, commentId, request)));
    }

    @PostMapping("/comments/{commentId}/resolve")
    @Operation(summary = "Resolve a comment thread")
    public ResponseEntity<ApiResponse<CommentResponse>> resolve(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.setResolved(user, workspaceId, commentId, true)));
    }

    @PostMapping("/comments/{commentId}/unresolve")
    @Operation(summary = "Reopen a resolved comment thread")
    public ResponseEntity<ApiResponse<CommentResponse>> unresolve(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.setResolved(user, workspaceId, commentId, false)));
    }

    @PostMapping("/comments/{commentId}/reactions")
    @Operation(summary = "Toggle an emoji reaction on a comment")
    public ResponseEntity<ApiResponse<CommentResponse>> react(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID commentId,
            @Valid @RequestBody ReactionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.toggleReaction(user, workspaceId, commentId,
                request.emoji())));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Delete a comment (author, or moderators with DELETE access)")
    public ResponseEntity<ApiResponse<Void>> delete(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID commentId) {
        commentService.delete(user, workspaceId, commentId);
        return ResponseEntity.ok(ApiResponse.message("Comment deleted"));
    }
}
