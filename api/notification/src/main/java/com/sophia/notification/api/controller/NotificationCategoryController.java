package com.sophia.notification.api.controller;

import com.sophia.notification.api.security.CurrentUser;
import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.application.service.NotificationCategoryService;
import com.sophia.notification.domain.dto.request.CreateNotificationCategoryRequest;
import com.sophia.notification.domain.dto.request.UpdateNotificationCategoryRequest;
import com.sophia.notification.domain.dto.response.ApiResponse;
import com.sophia.notification.domain.dto.response.NotificationCategoryResponse;
import com.sophia.notification.domain.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-categories")
@RequiredArgsConstructor
@Tag(name = "Notification categories",
        description = "Manage the set of categories notifications can be filed under")
public class NotificationCategoryController {

    private final NotificationCategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_CATEGORY_READ')")
    @Operation(summary = "List notification categories with search, sorting and pagination")
    public ResponseEntity<ApiResponse<PageResponse<NotificationCategoryResponse>>> list(
            @Parameter(description = "Free-text search over code, name and description")
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.list(search, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_CATEGORY_CREATE')")
    @Operation(summary = "Create a new notification category")
    public ResponseEntity<ApiResponse<NotificationCategoryResponse>> create(
            @CurrentUser AuthenticatedUser actor,
            @Valid @RequestBody CreateNotificationCategoryRequest request) {
        NotificationCategoryResponse response = categoryService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Notification category created"));
    }

    @PatchMapping("/{code}")
    @PreAuthorize("hasAuthority('NOTIFICATION_CATEGORY_UPDATE')")
    @Operation(summary = "Update a notification category's name, description or mandatory flag")
    public ResponseEntity<ApiResponse<NotificationCategoryResponse>> update(
            @CurrentUser AuthenticatedUser actor,
            @PathVariable String code,
            @Valid @RequestBody UpdateNotificationCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.update(code, request, actor),
                "Notification category updated"));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasAuthority('NOTIFICATION_CATEGORY_DELETE')")
    @Operation(summary = "Delete a notification category")
    public ResponseEntity<ApiResponse<Void>> delete(
            @CurrentUser AuthenticatedUser actor,
            @PathVariable String code) {
        categoryService.delete(code, actor);
        return ResponseEntity.ok(ApiResponse.message("Notification category deleted"));
    }
}
