package com.sophia.notification.api.controller;

import com.sophia.notification.api.security.CurrentUser;
import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.application.service.NotificationPreferenceService;
import com.sophia.notification.domain.dto.request.UpdateNotificationPreferencesRequest;
import com.sophia.notification.domain.dto.response.ApiResponse;
import com.sophia.notification.domain.dto.response.NotificationPreferencesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Notification preferences", description = "Per-user channel and category delivery preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping("/notification-preferences")
    @PreAuthorize("hasAuthority('NOTIFICATION_PREFERENCE_READ')")
    @Operation(summary = "Effective notification preferences of the caller")
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> get(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.getOwn(user.id())));
    }

    @PutMapping("/notification-preferences")
    @PreAuthorize("hasAuthority('NOTIFICATION_PREFERENCE_UPDATE')")
    @Operation(summary = "Update the caller's notification preferences")
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> update(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.updateOwn(user.id(), request)));
    }

    @GetMapping("/users/{userId}/notification-preferences")
    @PreAuthorize("hasAuthority('NOTIFICATION_PREFERENCE_READ_UNRESTRICTED')")
    @Operation(summary = "Effective notification preferences of the given user, unrestricted to the caller's own")
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> getUnrestricted(
            @CurrentUser AuthenticatedUser actor,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.getUnrestricted(userId, actor)));
    }

    @PutMapping("/users/{userId}/notification-preferences")
    @PreAuthorize("hasAuthority('NOTIFICATION_PREFERENCE_UPDATE_UNRESTRICTED')")
    @Operation(summary = "Update the given user's notification preferences on their behalf")
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> updateUnrestricted(
            @CurrentUser AuthenticatedUser actor,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceService.updateUnrestricted(userId, request, actor)));
    }
}
