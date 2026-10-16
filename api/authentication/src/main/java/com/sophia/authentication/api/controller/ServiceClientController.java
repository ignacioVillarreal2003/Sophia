package com.sophia.authentication.api.controller;

import com.sophia.authentication.application.service.ServiceClientService;
import com.sophia.authentication.domain.dto.request.BulkIdsRequest;
import com.sophia.authentication.domain.dto.request.ServiceClientRequest;
import com.sophia.authentication.domain.dto.response.ApiResponse;
import com.sophia.authentication.domain.dto.response.BulkOperationResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.ServiceClientResponse;
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
@RequestMapping("/api/v1/service-clients")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SERVICE_CLIENT_MANAGE')")
@Tag(name = "Service Clients", description = "Machine-to-machine client registry (client_credentials)")
public class ServiceClientController {

    private final ServiceClientService serviceClientService;

    @GetMapping
    @Operation(summary = "List service clients with search, sorting and pagination")
    public ResponseEntity<ApiResponse<PageResponse<ServiceClientResponse>>> list(
            @Parameter(description = "Free-text search over name and description") @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(serviceClientService.list(search, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a service client by id")
    public ResponseEntity<ApiResponse<ServiceClientResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(serviceClientService.get(id)));
    }

    @PostMapping
    @Operation(summary = "Register a service client",
            description = "Returns the client secret ONCE; it is stored only as a hash.")
    public ResponseEntity<ApiResponse<ServiceClientResponse>> create(
            @Valid @RequestBody ServiceClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(serviceClientService.create(request),
                        "Service client created. Store the secret now: it will not be shown again."));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a service client (name, description, scopes, active)")
    public ResponseEntity<ApiResponse<ServiceClientResponse>> update(@PathVariable UUID id,
                                                                     @Valid @RequestBody ServiceClientRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(serviceClientService.update(id, request), "Service client updated"));
    }

    @PostMapping("/{id}/rotate-secret")
    @Operation(summary = "Rotate the client secret",
            description = "Invalidates the previous secret immediately and returns the new one ONCE.")
    public ResponseEntity<ApiResponse<ServiceClientResponse>> rotateSecret(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(serviceClientService.rotateSecret(id),
                "Secret rotated. Store it now: it will not be shown again."));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a service client")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        serviceClientService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Service client deleted"));
    }

    @PostMapping("/bulk-delete")
    @Operation(summary = "Delete several service clients in one request",
            description = "Each id is processed independently; one failing does not abort the rest of the batch.")
    public ResponseEntity<ApiResponse<BulkOperationResponse>> bulkDelete(
            @Valid @RequestBody BulkIdsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(serviceClientService.bulkDelete(request.ids())));
    }
}
