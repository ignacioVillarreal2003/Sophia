package com.sophia.authentication.api.controller;

import com.sophia.authentication.domain.dto.request.AdminSetPasswordRequest;
import com.sophia.authentication.domain.dto.request.BulkIdsRequest;
import com.sophia.authentication.domain.dto.request.BulkUserPermissionsRequest;
import com.sophia.authentication.domain.dto.request.BulkUserRolesRequest;
import com.sophia.authentication.domain.dto.request.BulkUserStatusRequest;
import com.sophia.authentication.domain.dto.request.UpdateUserPermissionsRequest;
import com.sophia.authentication.domain.dto.request.UpdateUserRolesRequest;
import com.sophia.authentication.domain.dto.request.UpdateUserStatusRequest;
import com.sophia.authentication.domain.dto.request.CreateUserRequest;
import com.sophia.authentication.domain.dto.request.PatchUserRequest;
import com.sophia.authentication.domain.dto.request.UpdateUserRequest;
import com.sophia.authentication.domain.dto.response.ApiResponse;
import com.sophia.authentication.domain.dto.response.BulkOperationResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.SessionResponse;
import com.sophia.authentication.domain.dto.response.UserResponse;
import com.sophia.authentication.application.service.EmailVerificationService;
import com.sophia.authentication.application.service.GoogleAuthenticationService;
import com.sophia.authentication.application.service.PasswordService;
import com.sophia.authentication.application.service.SessionService;
import com.sophia.authentication.application.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User administration (RBAC protected)")
public class UserController {

    private final UserService userService;
    private final PasswordService passwordService;
    private final SessionService sessionService;
    private final EmailVerificationService emailVerificationService;
    private final GoogleAuthenticationService googleAuthenticationService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List users with search, filters, sorting and pagination")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> list(
            @Parameter(description = "Free-text search over name and email") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by active flag") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Filter by email verification") @RequestParam(required = false) Boolean emailVerified,
            @Parameter(description = "Filter by role name") @RequestParam(required = false) String role,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.list(search, active, emailVerified, role, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ') or #id == principal.id")
    @Operation(summary = "Get a user by id")
    public ResponseEntity<ApiResponse<UserResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Create a user")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(userService.create(request), "User created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Replace a user")
    public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.update(id, request), "User updated"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE') or #id == principal.id")
    @Operation(summary = "Partially update a user",
            description = "Users may update their own profile; USER_UPDATE is required for others. "
                    + "Note: the active flag is ignored for self-updates without USER_UPDATE.")
    public ResponseEntity<ApiResponse<UserResponse>> patch(@PathVariable UUID id,
                                                           @Valid @RequestBody PatchUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.patch(id, request), "User updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @Operation(summary = "Soft delete a user and revoke all their sessions")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("User deleted"));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @Operation(summary = "Soft delete several users in one request",
            description = "Each id is processed independently; one failing (e.g. the caller's own "
                    + "account) does not abort the rest of the batch.")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkDelete(
            @Valid @RequestBody BulkIdsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.bulkDelete(request.ids())));
    }

    @PostMapping("/bulk-status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Activate or deactivate several users in one request",
            description = "Deactivating revokes every session of each user. Each id is processed "
                    + "independently.")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkUpdateStatus(
            @Valid @RequestBody BulkUserStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.bulkUpdateStatus(request.ids(), request.active())));
    }

    @PostMapping("/bulk-roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Replace the role set of several users in one request",
            description = "Every listed user ends up with exactly this role set.")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkUpdateRoles(
            @Valid @RequestBody BulkUserRolesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.bulkUpdateRoles(request.ids(), request.roles())));
    }

    @PostMapping("/bulk-permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Add direct permissions to several users in one request",
            description = "These permissions are added on top of each user's existing direct grants.")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkAddPermissions(
            @Valid @RequestBody BulkUserPermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.bulkAddPermissions(request.ids(), request.permissions())));
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Replace a user's roles")
    public ResponseEntity<ApiResponse<UserResponse>> updateRoles(@PathVariable UUID id,
                                                                 @Valid @RequestBody UpdateUserRolesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateRoles(id, request.roles()), "Roles updated"));
    }

    @PatchMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Replace a user's direct permission grants")
    public ResponseEntity<ApiResponse<UserResponse>> updatePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserPermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.updatePermissions(id, request.permissions()), "Permissions updated"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Activate or deactivate a user",
            description = "Deactivating revokes every session of the user.")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(@PathVariable UUID id,
                                                                  @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                userService.updateStatus(id, request.active()), "Status updated"));
    }

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasAuthority('USER_PASSWORD_RESET')")
    @Operation(summary = "Directly set another user's password",
            description = "No current password required. Revokes every session of the target user "
                    + "and notifies them by email.")
    public ResponseEntity<ApiResponse<Void>> setPassword(@PathVariable UUID id,
                                                          @Valid @RequestBody AdminSetPasswordRequest request) {
        passwordService.adminSetPassword(id, request.newPassword());
        return ResponseEntity.ok(ApiResponse.message("Password updated"));
    }

    @GetMapping("/{id}/sessions")
    @PreAuthorize("hasAuthority('SESSION_READ')")
    @Operation(summary = "List a user's active sessions")
    public ResponseEntity<ApiResponse<PageResponse<SessionResponse>>> listSessions(
            @PathVariable UUID id,
            @ParameterObject @PageableDefault(size = 20, sort = "lastActiveAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.listMySessions(id, null, pageable)));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('SESSION_MANAGE')")
    @Operation(summary = "Revoke one of a user's sessions")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        sessionService.revokeMySession(id, sessionId);
        return ResponseEntity.ok(ApiResponse.message("Session revoked"));
    }

    @DeleteMapping("/{id}/sessions")
    @PreAuthorize("hasAuthority('SESSION_MANAGE')")
    @Operation(summary = "Revoke every active session of a user")
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions(@PathVariable UUID id) {
        sessionService.revokeAllMySessionsExceptCurrent(id, null);
        return ResponseEntity.ok(ApiResponse.message("Sessions revoked"));
    }

    @PostMapping("/{id}/resend-verification")
    @PreAuthorize("hasAuthority('USER_EMAIL_VERIFY')")
    @Operation(summary = "Resend the email verification link to a user")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@PathVariable UUID id) {
        emailVerificationService.adminResendVerification(id);
        return ResponseEntity.ok(ApiResponse.message("Verification email sent"));
    }

    @PatchMapping("/{id}/verify-email")
    @PreAuthorize("hasAuthority('USER_EMAIL_VERIFY')")
    @Operation(summary = "Mark a user's email as verified",
            description = "Sets the flag directly, without going through the token flow.")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@PathVariable UUID id) {
        emailVerificationService.adminVerifyEmail(id);
        return ResponseEntity.ok(ApiResponse.message("Email marked as verified"));
    }

    @PatchMapping("/{id}/unverify-email")
    @PreAuthorize("hasAuthority('USER_EMAIL_VERIFY')")
    @Operation(summary = "Mark a user's email as unverified")
    public ResponseEntity<ApiResponse<Void>> unverifyEmail(@PathVariable UUID id) {
        emailVerificationService.adminUnverifyEmail(id);
        return ResponseEntity.ok(ApiResponse.message("Email marked as unverified"));
    }

    @DeleteMapping("/{id}/google")
    @PreAuthorize("hasAuthority('USER_GOOGLE_UNLINK')")
    @Operation(summary = "Force-unlink a user's Google account",
            description = "Fails if the user has no password set, since that would lock them out.")
    public ResponseEntity<ApiResponse<Void>> unlinkGoogle(@PathVariable UUID id) {
        googleAuthenticationService.unlink(id);
        return ResponseEntity.ok(ApiResponse.message("Google account unlinked"));
    }

    @PatchMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER_UPDATE') or #id == principal.id")
    @Operation(summary = "Upload a profile photo",
            description = "Accepts JPEG, PNG or WebP up to 5 MB.")
    public ResponseEntity<ApiResponse<UserResponse>> updatePhoto(@PathVariable UUID id,
                                                                 @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updatePhoto(id, file), "Photo updated"));
    }

    @DeleteMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('USER_UPDATE') or #id == principal.id")
    @Operation(summary = "Remove the profile photo")
    public ResponseEntity<ApiResponse<UserResponse>> deletePhoto(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.deletePhoto(id), "Photo removed"));
    }
}
