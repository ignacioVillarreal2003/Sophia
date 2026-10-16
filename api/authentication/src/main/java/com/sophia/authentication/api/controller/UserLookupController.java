package com.sophia.authentication.api.controller;

import com.sophia.authentication.domain.dto.response.ApiResponse;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Minimal directory lookup other modules use to resolve an email to a user (no admin permission needed). */
@RestController
@RequestMapping("/api/v1/users/lookup")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User administration (RBAC protected)")
public class UserLookupController {

    private final UserRepository userRepository;

    public record UserSummary(UUID id, String email, String fullName, String photoUrl) {
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Resolve an active user by exact email (id, email, name and photo only)")
    public ResponseEntity<ApiResponse<UserSummary>> lookup(@RequestParam String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .filter(u -> u.isActive())
                .map(u -> ResponseEntity.ok(ApiResponse.ok(new UserSummary(u.getId(), u.getEmail(),
                        u.getFullName(), u.getPreferences() == null ? null : u.getPreferences().getPhotoUrl()))))
                .orElseThrow(() -> new ResourceNotFoundException("No user with that email"));
    }
}
