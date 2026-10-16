package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.FavoriteService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.FavoriteResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@RequiredArgsConstructor
@Validated
@Tag(name = "Favorites", description = "Favorites, pinned items and recently opened artifacts")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PutMapping("/favorites/{artifactId}")
    @Operation(summary = "Add an artifact to the caller's favorites")
    public ResponseEntity<ApiResponse<FavoriteResponse>> addFavorite(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.addFavorite(user, workspaceId, artifactId)));
    }

    @DeleteMapping("/favorites/{artifactId}")
    @Operation(summary = "Remove an artifact from the caller's favorites")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId) {
        favoriteService.removeFavorite(user, workspaceId, artifactId);
        return ResponseEntity.ok(ApiResponse.message("Favorite removed"));
    }

    @PutMapping("/favorites/{artifactId}/pinned")
    @Operation(summary = "Pin or unpin a favorite")
    public ResponseEntity<ApiResponse<FavoriteResponse>> setPinned(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @PathVariable UUID artifactId,
            @RequestParam boolean pinned) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.setPinned(user, workspaceId, artifactId, pinned)));
    }

    @GetMapping("/favorites")
    @Operation(summary = "List the caller's favorites in this workspace")
    public ResponseEntity<ApiResponse<PageResponse<FavoriteResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean pinnedOnly,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.list(user, workspaceId, pinnedOnly, pageable)));
    }

    @GetMapping("/recent")
    @Operation(summary = "List the caller's recently opened artifacts, newest first")
    public ResponseEntity<ApiResponse<List<ArtifactResponse>>> listRecent(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.listRecent(user, workspaceId, limit)));
    }
}
