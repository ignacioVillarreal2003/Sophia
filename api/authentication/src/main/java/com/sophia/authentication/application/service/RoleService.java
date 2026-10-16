package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.dto.request.RoleRequest;
import com.sophia.authentication.domain.dto.response.BulkOperationResponse;
import com.sophia.authentication.domain.dto.response.RoleResponse;
import com.sophia.authentication.domain.entity.Permission;
import com.sophia.authentication.domain.entity.Role;
import com.sophia.authentication.domain.exception.BadRequestException;
import com.sophia.authentication.domain.exception.DuplicateResourceException;
import com.sophia.authentication.domain.exception.OperationNotAllowedException;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.application.mapper.RoleMapper;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.infrastructure.config.CacheNames;
import com.sophia.authentication.infrastructure.repository.PermissionRepository;
import com.sophia.authentication.infrastructure.repository.RoleRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.repository.specification.RoleSpecifications;
import com.sophia.authentication.domain.util.AuditActions;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RoleMapper roleMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> list(String search, Pageable pageable) {
        Specification<Role> spec = RoleSpecifications.search(search);
        return PageResponse.from(roleRepository.findAll(spec, pageable), roleMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.ROLES, key = "#id")
    public RoleResponse get(UUID id) {
        return roleMapper.toResponse(load(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A role with this name already exists");
        }
        Role role = Role.create(request.name(), request.description(), false,
                resolvePermissions(request.permissions()));
        role = roleRepository.save(role);
        auditService.record(null, null, AuditActions.ROLE_CREATED, "Role", role.getId().toString(),
                AuditLog.Result.SUCCESS, Map.of("name", role.getName()));
        return roleMapper.toResponse(role);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.ROLES, key = "#id")
    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = load(id);
        if (role.isSystem() && !role.getName().equals(request.name())) {
            throw new OperationNotAllowedException("System roles cannot be renamed");
        }
        if (!role.getName().equals(request.name()) && roleRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A role with this name already exists");
        }

        Map<String, Object> before = new HashMap<>();
        before.put("name", role.getName());
        before.put("description", role.getDescription() == null ? "" : role.getDescription());
        before.put("permissions", permissionNames(role.getPermissions()));

        role.update(request.name(), request.description(), resolvePermissions(request.permissions()));

        auditService.record(null, null, AuditActions.ROLE_UPDATED, "Role", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", before));
        return roleMapper.toResponse(role);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.ROLES, key = "#id")
    public void delete(UUID id) {
        Role role = load(id);
        if (role.isSystem()) {
            throw new OperationNotAllowedException("System roles cannot be deleted");
        }
        long assignedUsers = userRepository.countByRoles_Id(id);
        if (assignedUsers > 0) {
            throw new OperationNotAllowedException(
                    "Role is assigned to " + assignedUsers + " user(s); unassign it first");
        }
        roleRepository.delete(role);
        auditService.record(null, null, AuditActions.ROLE_DELETED, "Role", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("name", role.getName()));
    }

    @Transactional
    public BulkOperationResponse bulkDelete(List<UUID> ids) {
        int successCount = 0;
        List<BulkOperationResponse.Failure> failures = new ArrayList<>();
        for (UUID id : ids) {
            try {
                delete(id);
                successCount++;
            } catch (RuntimeException e) {
                failures.add(new BulkOperationResponse.Failure(id, e.getMessage()));
            }
        }
        return BulkOperationResponse.of(successCount, failures);
    }

    private Set<String> permissionNames(Set<Permission> permissions) {
        return permissions.stream().map(Permission::getName).collect(Collectors.toSet());
    }

    private Set<Permission> resolvePermissions(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new HashSet<>();
        }
        List<Permission> permissions = permissionRepository.findByNameIn(names);
        if (permissions.size() != names.size()) {
            Set<String> found = permissions.stream().map(Permission::getName).collect(Collectors.toSet());
            Set<String> missing = new HashSet<>(names);
            missing.removeAll(found);
            throw new BadRequestException("Unknown permissions: " + String.join(", ", missing));
        }
        return new HashSet<>(permissions);
    }

    private Role load(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }
}
