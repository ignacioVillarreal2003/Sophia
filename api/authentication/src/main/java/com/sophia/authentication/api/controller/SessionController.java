package com.sophia.authentication.api.controller;

import com.sophia.authentication.domain.dto.response.ApiResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.SessionResponse;
import com.sophia.authentication.application.security.CustomUserDetails;
import com.sophia.authentication.application.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "Self-service device session management")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    @Operation(summary = "List the caller's active sessions")
    public ResponseEntity<ApiResponse<PageResponse<SessionResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @ParameterObject @PageableDefault(size = 20, sort = "lastActiveAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                sessionService.listMySessions(principal.getId(), principal.getSessionId(), pageable)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke one of the caller's sessions")
    public ResponseEntity<ApiResponse<Void>> revoke(@AuthenticationPrincipal CustomUserDetails principal,
                                                    @PathVariable UUID id) {
        sessionService.revokeMySession(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.message("Session revoked"));
    }

    @DeleteMapping
    @Operation(summary = "Revoke every session of the caller except the current one")
    public ResponseEntity<ApiResponse<Void>> revokeAll(@AuthenticationPrincipal CustomUserDetails principal) {
        sessionService.revokeAllMySessionsExceptCurrent(principal.getId(), principal.getSessionId());
        return ResponseEntity.ok(ApiResponse.message("Other sessions revoked"));
    }
}
