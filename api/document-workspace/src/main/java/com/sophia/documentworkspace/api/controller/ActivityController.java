package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.CurrentUser;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.ActivityFeedService;
import com.sophia.documentworkspace.domain.dto.response.ActivityItem;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspace-activity")
@RequiredArgsConstructor
@Tag(name = "Activity", description = "Recent activity across the caller's workspaces")
public class ActivityController {

    private final ActivityFeedService feedService;

    @GetMapping
    @Operation(summary = "Latest changes by other people in the caller's workspaces")
    public ResponseEntity<ApiResponse<List<ActivityItem>>> recent(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "false") boolean includeOwn) {
        return ResponseEntity.ok(ApiResponse.ok(feedService.recent(user, limit, includeOwn)));
    }
}
