package com.sophia.authentication.api.controller;

import com.sophia.authentication.domain.dto.request.BulkIdsRequest;
import com.sophia.authentication.domain.dto.request.RoleRequest;
import com.sophia.authentication.domain.dto.response.ApiResponse;
import com.sophia.authentication.domain.dto.response.BulkOperationResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.RoleResponse;
import com.sophia.authentication.application.service.RoleService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role management (RBAC)")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "List roles with search, sorting and pagination")
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> list(
            @Parameter(description = "Free-text search over name and description") @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.list(search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "Get a role by id")
    public ResponseEntity<ApiResponse<RoleResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Create a role")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(roleService.create(request), "Role created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Update a role", description = "System roles cannot be renamed.")
    public ResponseEntity<ApiResponse<RoleResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.update(id, request), "Role updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Delete a role",
            description = "System roles and roles still assigned to users cannot be deleted.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Role deleted"));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Delete several roles in one request",
            description = "Each id is processed independently; one failing (system role, still "
                    + "assigned to users) does not abort the rest of the batch.")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkDelete(
            @Valid @RequestBody BulkIdsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.bulkDelete(request.ids())));
    }
}
