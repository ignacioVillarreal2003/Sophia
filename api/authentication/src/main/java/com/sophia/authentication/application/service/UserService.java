package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.dto.request.CreateUserRequest;
import com.sophia.authentication.domain.dto.request.PatchUserRequest;
import com.sophia.authentication.domain.dto.request.UpdateUserRequest;
import com.sophia.authentication.domain.dto.response.BulkOperationResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.UserResponse;
import com.sophia.authentication.domain.entity.Permission;
import com.sophia.authentication.domain.entity.Role;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserPreferences;
import com.sophia.authentication.domain.entity.enums.AuthenticationProvider;
import com.sophia.authentication.domain.entity.enums.Theme;
import com.sophia.authentication.domain.entity.enums.ThemeMode;
import com.sophia.authentication.domain.exception.BadRequestException;
import com.sophia.authentication.domain.exception.DuplicateResourceException;
import com.sophia.authentication.domain.exception.OperationNotAllowedException;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.application.mapper.UserMapper;
import com.sophia.authentication.infrastructure.repository.PermissionRepository;
import com.sophia.authentication.infrastructure.repository.RoleRepository;
import com.sophia.authentication.infrastructure.repository.UserPreferencesRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.repository.specification.UserSpecifications;
import com.sophia.authentication.application.security.SecurityUtilities;
import com.sophia.authentication.infrastructure.messaging.UserEventPublisher;
import com.sophia.authentication.infrastructure.messaging.UserEventType;
import com.sophia.authentication.infrastructure.repository.OAuthAccountRepository;
import com.sophia.authentication.infrastructure.service.StorageService;
import com.sophia.authentication.domain.util.AuditActions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("es", "en");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final StorageService storageService;
    private final AuditService auditService;
    private final UserMapper userMapper;
    private final OAuthAccountRepository oauthAccountRepository;
    private final UserEventPublisher userEventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search,
                                           Boolean active,
                                           Boolean emailVerified,
                                           String role,
                                           Pageable pageable) {
        Specification<User> spec = UserSpecifications.search(search)
                .and(UserSpecifications.hasActive(active))
                .and(UserSpecifications.hasEmailVerified(emailVerified))
                .and(UserSpecifications.hasRole(role));
        return PageResponse.from(userRepository.findAll(spec, pageable), userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return userMapper.toResponse(loadWithAuthorities(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        Set<String> roleNames = (request.roles() == null || request.roles().isEmpty())
                ? Set.of(AuthenticationService.DEFAULT_ROLE)
                : request.roles();
        User user = User.create(
                request.firstName().trim(),
                request.lastName().trim(),
                request.email().toLowerCase().trim(),
                passwordEncoder.encode(request.password()),
                AuthenticationProvider.LOCAL,
                request.active() == null || request.active(),
                Boolean.TRUE.equals(request.emailVerified()),
                resolveRoles(roleNames));
        user = userRepository.save(user);
        user.assignPreferences(createDefaultPreferences(user));
        auditService.record(null, null, AuditActions.USER_CREATED, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, Map.of("email", user.getEmail()));
        userEventPublisher.publish(UserEventType.USER_CREATED, user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = loadWithAuthorities(id);
        Map<String, Object> before = snapshot(user);
        applyEmailChange(user, request.email());
        user.updateProfile(request.firstName().trim(), request.lastName().trim());
        user.updateActiveStatus(request.active());
        applyPreferences(user, request.theme(), request.themeMode(), request.locale(), request.timezone(),
                request.hiddenModules());
        auditService.record(null, null, AuditActions.USER_UPDATED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", before));
        userEventPublisher.publish(UserEventType.USER_UPDATED, user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse patch(UUID id, PatchUserRequest request) {
        User user = loadWithAuthorities(id);
        Map<String, Object> before = snapshot(user);
        if (StringUtils.hasText(request.email())) {
            applyEmailChange(user, request.email());
        }
        user.updateProfile(
                StringUtils.hasText(request.firstName()) ? request.firstName().trim() : null,
                StringUtils.hasText(request.lastName()) ? request.lastName().trim() : null);
        if (request.active() != null && canChangeActiveFlag(id)) {
            user.updateActiveStatus(request.active());
            if (!request.active()) {
                tokenService.revokeAllForUser(id);
            }
        }
        applyPreferences(user, request.theme(), request.themeMode(), request.locale(), request.timezone(),
                request.hiddenModules());
        auditService.record(null, null, AuditActions.USER_UPDATED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", before));
        userEventPublisher.publish(UserEventType.USER_UPDATED, user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void delete(UUID id) {
        User user = load(id);
        UUID currentUserId = SecurityUtilities.getCurrentUserId().orElse(null);
        if (id.equals(currentUserId)) {
            throw new OperationNotAllowedException("Use DELETE /api/v1/authentication/account to delete your own account");
        }
        Map<String, Object> before = snapshot(user);
        tokenService.revokeAllForUser(id);
        String email = user.getEmail();
        oauthAccountRepository.deleteByUserId(id);
        user.softDelete(SecurityUtilities.getCurrentUserEmail().orElse("system"));
        userRepository.save(user);
        auditService.record(null, null, AuditActions.USER_DELETED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", before));
        userEventPublisher.publish(UserEventType.USER_DELETED, id, email);
    }

    @Transactional
    public UserResponse updateRoles(UUID id, Set<String> roleNames) {
        User user = loadWithAuthorities(id);
        Set<String> oldRoleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        user.updateRoles(resolveRoles(roleNames));
        auditService.record(null, null, AuditActions.USER_ROLES_UPDATED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", Map.of("roles", oldRoleNames), "roles", List.copyOf(roleNames)));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updatePermissions(UUID id, Set<String> permissionNames) {
        User user = loadWithAuthorities(id);
        Set<String> oldPermissionNames = user.getDirectPermissions().stream()
                .map(Permission::getName).collect(Collectors.toSet());
        user.updateDirectPermissions(resolvePermissions(permissionNames));
        auditService.record(null, null, AuditActions.USER_PERMISSIONS_UPDATED, "User", id.toString(),
                AuditLog.Result.SUCCESS,
                Map.of("before", Map.of("permissions", oldPermissionNames), "permissions", List.copyOf(permissionNames)));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, boolean active) {
        User user = loadWithAuthorities(id);
        UUID currentUserId = SecurityUtilities.getCurrentUserId().orElse(null);
        if (!active && id.equals(currentUserId)) {
            throw new OperationNotAllowedException("You cannot deactivate your own account");
        }
        boolean wasActive = user.isActive();
        user.updateActiveStatus(active);
        if (!active) {
            tokenService.revokeAllForUser(id);
        }
        auditService.record(null, null, AuditActions.USER_STATUS_UPDATED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", Map.of("active", wasActive), "active", active));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updatePhoto(UUID id, MultipartFile file) {
        User user = loadWithAuthorities(id);
        UserPreferences preferences = user.getPreferences();
        String previousPhotoUrl = preferences.getPhotoUrl();
        String filename = storageService.storeAvatar(file);
        deleteStoredPhoto(previousPhotoUrl);
        preferences.updatePhoto("/files/" + filename);
        auditService.record(null, null, AuditActions.USER_PHOTO_UPDATED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", Map.of("photoUrl", orEmpty(previousPhotoUrl))));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse deletePhoto(UUID id) {
        User user = loadWithAuthorities(id);
        UserPreferences preferences = user.getPreferences();
        String previousPhotoUrl = preferences.getPhotoUrl();
        deleteStoredPhoto(previousPhotoUrl);
        preferences.updatePhoto(null);
        auditService.record(null, null, AuditActions.USER_PHOTO_UPDATED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", Map.of("photoUrl", orEmpty(previousPhotoUrl))));
        return userMapper.toResponse(user);
    }

    private Map<String, Object> snapshot(User user) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("firstName", user.getFirstName());
        fields.put("lastName", user.getLastName());
        fields.put("email", user.getEmail());
        fields.put("active", user.isActive());
        return fields;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    @Transactional
    public BulkOperationResponse bulkDelete(List<UUID> ids) {
        return runBulk(ids, this::delete);
    }

    @Transactional
    public BulkOperationResponse bulkUpdateStatus(List<UUID> ids, boolean active) {
        return runBulk(ids, id -> updateStatus(id, active));
    }

    @Transactional
    public BulkOperationResponse bulkUpdateRoles(List<UUID> ids, Set<String> roleNames) {
        return runBulk(ids, id -> updateRoles(id, roleNames));
    }

    @Transactional
    public BulkOperationResponse bulkAddPermissions(List<UUID> ids, Set<String> permissionNames) {
        return runBulk(ids, id -> {
            User user = loadWithAuthorities(id);
            Set<String> merged = new HashSet<>(permissionNames);
            user.getDirectPermissions().forEach(p -> merged.add(p.getName()));
            updatePermissions(id, merged);
        });
    }

    private BulkOperationResponse runBulk(List<UUID> ids, Consumer<UUID> action) {
        int successCount = 0;
        List<BulkOperationResponse.Failure> failures = new ArrayList<>();
        for (UUID id : ids) {
            try {
                action.accept(id);
                successCount++;
            } catch (RuntimeException e) {
                failures.add(new BulkOperationResponse.Failure(id, e.getMessage()));
            }
        }
        return BulkOperationResponse.of(successCount, failures);
    }

    private void applyEmailChange(User user, String newEmail) {
        String normalized = newEmail.toLowerCase().trim();
        if (!normalized.equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailIgnoreCase(normalized)) {
                throw new DuplicateResourceException("An account with this email already exists");
            }
            user.changeEmail(normalized);
            userEventPublisher.publish(UserEventType.USER_EMAIL_CHANGED, user.getId(), normalized);
        }
    }

    private Set<Role> resolveRoles(Set<String> names) {
        List<Role> roles = roleRepository.findByNameIn(names);
        if (roles.size() != names.size()) {
            Set<String> found = roles.stream().map(Role::getName).collect(Collectors.toSet());
            Set<String> missing = new HashSet<>(names);
            missing.removeAll(found);
            throw new BadRequestException("Unknown roles: " + String.join(", ", missing));
        }
        return new HashSet<>(roles);
    }

    private Set<Permission> resolvePermissions(Set<String> names) {
        List<Permission> permissions = permissionRepository.findByNameIn(names);
        if (permissions.size() != names.size()) {
            Set<String> found = permissions.stream().map(Permission::getName).collect(Collectors.toSet());
            Set<String> missing = new HashSet<>(names);
            missing.removeAll(found);
            throw new BadRequestException("Unknown permissions: " + String.join(", ", missing));
        }
        return new HashSet<>(permissions);
    }

    private boolean canChangeActiveFlag(UUID targetId) {
        boolean isSelf = targetId.equals(SecurityUtilities.getCurrentUserId().orElse(null));
        boolean hasUserUpdate = SecurityUtilities.currentUser()
                .map(user -> user.getAuthorities().stream()
                        .anyMatch(authority -> "USER_UPDATE".equals(authority.getAuthority())))
                .orElse(false);
        return hasUserUpdate && !isSelf;
    }

    private void deleteStoredPhoto(String photoUrl) {
        if (photoUrl != null && photoUrl.startsWith("/files/")) {
            storageService.delete(photoUrl.substring("/files/".length()));
        }
    }

    private UserPreferences createDefaultPreferences(User user) {
        return userPreferencesRepository.save(UserPreferences.create(user, null));
    }

    private void applyPreferences(User user, Theme theme, ThemeMode themeMode, String locale, String timezone,
                                  List<String> hiddenModules) {
        if (StringUtils.hasText(locale) && !SUPPORTED_LOCALES.contains(locale)) {
            throw new BadRequestException("Unknown locale: " + locale);
        }
        if (StringUtils.hasText(timezone) && !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new BadRequestException("Unknown timezone: " + timezone);
        }
        user.getPreferences().updateSettings(theme, themeMode,
                StringUtils.hasText(locale) ? locale : null,
                StringUtils.hasText(timezone) ? timezone : null);
        // Deduplicated so a client that sends the same slug twice cannot grow the
        // row unbounded, and ordered so the stored value is stable to compare.
        user.getPreferences().updateHiddenModules(hiddenModules == null ? null
                : hiddenModules.stream().distinct().sorted().toList());
    }

    private User load(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private User loadWithAuthorities(UUID id) {
        return userRepository.findByIdWithAuthorities(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
