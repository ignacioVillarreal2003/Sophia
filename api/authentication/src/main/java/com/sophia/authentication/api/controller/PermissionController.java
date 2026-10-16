package com.sophia.authentication.api.controller;

import com.sophia.authentication.domain.dto.request.BulkIdsRequest;
import com.sophia.authentication.domain.dto.request.PermissionRequest;
import com.sophia.authentication.domain.dto.response.ApiResponse;
import com.sophia.authentication.domain.dto.response.BulkOperationResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.PermissionResponse;
import com.sophia.authentication.application.service.PermissionService;
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
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Granular permission management")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    @Operation(summary = "List permissions with search, sorting and pagination")
    public ResponseEntity<ApiResponse<PageResponse<PermissionResponse>>> list(
            @Parameter(description = "Free-text search over name, description and category") @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = {"category", "name"}) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.list(search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    @Operation(summary = "Get a permission by id")
    public ResponseEntity<ApiResponse<PermissionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(summary = "Create a permission")
    public ResponseEntity<ApiResponse<PermissionResponse>> create(@Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(permissionService.create(request), "Permission created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(summary = "Update a permission")
    public ResponseEntity<ApiResponse<PermissionResponse>> update(@PathVariable UUID id,
                                                                  @Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.update(id, request), "Permission updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(summary = "Delete a permission",
            description = "Fails while the permission is still directly granted to users.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        permissionService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Permission deleted"));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(summary = "Delete several permissions in one request",
            description = "Each id is processed independently; one still directly granted to a "
                    + "user does not abort the rest of the batch.")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkDelete(
            @Valid @RequestBody BulkIdsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.bulkDelete(request.ids())));
    }
}
