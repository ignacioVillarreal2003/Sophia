package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.domain.dto.request.PermissionRequest;
import com.sophia.authentication.domain.dto.response.BulkOperationResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.PermissionResponse;
import com.sophia.authentication.domain.entity.Permission;
import com.sophia.authentication.domain.exception.DuplicateResourceException;
import com.sophia.authentication.domain.exception.OperationNotAllowedException;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.application.mapper.PermissionMapper;
import com.sophia.authentication.infrastructure.config.CacheNames;
import com.sophia.authentication.infrastructure.repository.PermissionRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.repository.specification.PermissionSpecifications;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PermissionMapper permissionMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<PermissionResponse> list(String search, Pageable pageable) {
        Specification<Permission> spec = PermissionSpecifications.search(search);
        return PageResponse.from(permissionRepository.findAll(spec, pageable), permissionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PERMISSIONS, key = "#id")
    public PermissionResponse get(UUID id) {
        return permissionMapper.toResponse(load(id));
    }

    @Transactional
    public PermissionResponse create(PermissionRequest request) {
        if (permissionRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A permission with this name already exists");
        }
        Permission permission = Permission.create(request.name(), request.description(), request.category());
        permission = permissionRepository.save(permission);
        auditService.record(null, null, AuditActions.PERMISSION_CREATED, "Permission", permission.getId().toString(),
                AuditLog.Result.SUCCESS, Map.of("name", permission.getName()));
        return permissionMapper.toResponse(permission);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PERMISSIONS, key = "#id")
    public PermissionResponse update(UUID id, PermissionRequest request) {
        Permission permission = load(id);
        if (!permission.getName().equals(request.name())
                && permissionRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A permission with this name already exists");
        }
        Map<String, Object> before = new HashMap<>();
        before.put("name", permission.getName());
        before.put("description", permission.getDescription() == null ? "" : permission.getDescription());
        before.put("category", permission.getCategory() == null ? "" : permission.getCategory());

        permission.update(request.name(), request.description(), request.category());
        auditService.record(null, null, AuditActions.PERMISSION_UPDATED, "Permission", id.toString(),
                AuditLog.Result.SUCCESS, Map.of("before", before));
        return permissionMapper.toResponse(permission);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PERMISSIONS, key = "#id")
    public void delete(UUID id) {
        Permission permission = load(id);
        long directGrants = userRepository.countByDirectPermissions_Id(id);
        if (directGrants > 0) {
            throw new OperationNotAllowedException(
                    "Permission is directly granted to " + directGrants + " user(s); revoke it first");
        }
        permissionRepository.delete(permission);
        auditService.record(null, null, AuditActions.PERMISSION_DELETED, "Permission", id.toString(),
                AuditLog.Result.SUCCESS, Map.of("name", permission.getName()));
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

    private Permission load(UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", id));
    }
}
