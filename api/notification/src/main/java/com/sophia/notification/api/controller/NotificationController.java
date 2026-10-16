package com.sophia.notification.api.controller;

import com.sophia.notification.api.security.CurrentUser;
import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.application.service.NotificationInboxService;
import com.sophia.notification.domain.dto.request.NotificationSearchRequest;
import com.sophia.notification.domain.dto.response.ApiResponse;
import com.sophia.notification.domain.dto.response.NotificationDispatchResponse;
import com.sophia.notification.domain.dto.response.NotificationGroupResponse;
import com.sophia.notification.domain.dto.response.NotificationResponse;
import com.sophia.notification.domain.dto.response.PageResponse;
import com.sophia.notification.infrastructure.service.ServerSentEventRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-application notifications: inbox, read state and realtime stream")
public class NotificationController {

    private final NotificationInboxService inboxService;
    private final ServerSentEventRegistry serverSentEventRegistry;

    @GetMapping("/notifications")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Search the caller's notifications",
            description = "Sortable by createdAt, readAt, priority, severity, category, type or title; "
                    + "newest first by default. Scheduled and expired notifications are never returned.")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @CurrentUser AuthenticatedUser user,
            @ParameterObject @Valid NotificationSearchRequest request,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.search(user.id(), request, pageable)));
    }

    @GetMapping("/notifications/groups")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "The caller's notifications collapsed by group key, most recent group first")
    public ResponseEntity<ApiResponse<List<NotificationGroupResponse>>> groups(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.groups(user.id(), unreadOnly)));
    }

    @GetMapping("/notifications/unread-count")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Number of unread notifications")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unread", inboxService.unreadCount(user.id()))));
    }

    @PostMapping("/notifications/{notificationId}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_UPDATE')")
    @Operation(summary = "Mark one notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.markRead(user.id(), notificationId)));
    }

    @PostMapping("/notifications/read-all")
    @PreAuthorize("hasAuthority('NOTIFICATION_UPDATE')")
    @Operation(summary = "Mark every notification as read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("marked", inboxService.markAllRead(user.id()))));
    }

    @PostMapping("/notifications/groups/{groupKey}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_UPDATE')")
    @Operation(summary = "Mark every notification in one group as read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markGroupRead(
            @CurrentUser AuthenticatedUser user,
            @PathVariable String groupKey) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("marked", inboxService.markGroupRead(user.id(), groupKey))));
    }

    @DeleteMapping("/notifications/{notificationId}")
    @PreAuthorize("hasAuthority('NOTIFICATION_DELETE')")
    @Operation(summary = "Delete one notification")
    public ResponseEntity<ApiResponse<Void>> delete(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID notificationId) {
        inboxService.delete(user.id(), notificationId);
        return ResponseEntity.ok(ApiResponse.message("Notification deleted"));
    }

    @GetMapping("/notifications/{notificationId}/dispatches")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Per-channel delivery status for one notification (pending/sent/failed, attempts, last error)")
    public ResponseEntity<ApiResponse<List<NotificationDispatchResponse>>> dispatches(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.listDispatches(user.id(), notificationId)));
    }

    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Server-sent events stream of the caller's notifications")
    public SseEmitter stream(@CurrentUser AuthenticatedUser user) {
        return serverSentEventRegistry.register(user.id());
    }

    @GetMapping("/users/{userId}/notifications")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ_UNRESTRICTED')")
    @Operation(summary = "Search the given user's notifications, unrestricted to the caller's own inbox",
            description = "Every call is written to the audit log.")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> listUnrestricted(
            @CurrentUser AuthenticatedUser actor,
            @PathVariable UUID userId,
            @ParameterObject @Valid NotificationSearchRequest request,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                inboxService.searchUnrestricted(userId, request, pageable, actor)));
    }

    @GetMapping("/notifications/by-correlation/{correlationId}")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ_UNRESTRICTED')")
    @Operation(summary = "Delivery records produced by one request, for following a report end to end")
    public ResponseEntity<ApiResponse<List<NotificationDispatchResponse>>> byCorrelation(
            @Parameter(description = "The X-Request-Id of the originating request")
            @PathVariable String correlationId) {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.listDispatchesByCorrelation(correlationId)));
    }
}
