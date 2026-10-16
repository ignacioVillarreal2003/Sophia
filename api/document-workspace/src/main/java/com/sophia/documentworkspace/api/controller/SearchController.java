package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.SearchService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.SearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/search")
@RequiredArgsConstructor
@Validated
@Tag(name = "Search", description = "Full-text search over titles, content, tags and metadata")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "Full-text search scoped to the workspace")
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID workspaceId,
            @RequestParam("q") @NotBlank @Size(max = 500) String query,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ResponseEntity.ok(ApiResponse.ok(searchService.search(user, workspaceId, query, types, tags,
                limit, offset)));
    }
}
