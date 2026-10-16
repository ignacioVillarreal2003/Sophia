package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.application.security.JsonWebTokenService;
import com.sophia.authentication.domain.dto.request.ServiceClientRequest;
import com.sophia.authentication.domain.dto.response.BulkOperationResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.ServiceClientResponse;
import com.sophia.authentication.domain.dto.response.TokenResponse;
import com.sophia.authentication.domain.entity.Permission;
import com.sophia.authentication.domain.entity.ServiceClient;
import com.sophia.authentication.domain.exception.BadRequestException;
import com.sophia.authentication.domain.exception.DuplicateResourceException;
import com.sophia.authentication.domain.exception.InvalidCredentialsException;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.domain.util.AuditActions;
import com.sophia.authentication.domain.util.TokenHasher;
import com.sophia.authentication.infrastructure.observability.AuthenticationMetrics;
import com.sophia.authentication.infrastructure.repository.PermissionRepository;
import com.sophia.authentication.infrastructure.repository.ServiceClientRepository;
import com.sophia.authentication.infrastructure.repository.specification.ServiceClientSpecifications;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@Slf4j
public class ServiceClientService {

    private final ServiceClientRepository serviceClientRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JsonWebTokenService jsonWebTokenService;
    private final AuditService auditService;
    private final AuthenticationMetrics authenticationMetrics;

    private final String timingEqualizationHash;

    public ServiceClientService(ServiceClientRepository serviceClientRepository,
                                PermissionRepository permissionRepository,
                                PasswordEncoder passwordEncoder,
                                JsonWebTokenService jsonWebTokenService,
                                AuditService auditService,
                                AuthenticationMetrics authenticationMetrics) {
        this.serviceClientRepository = serviceClientRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jsonWebTokenService = jsonWebTokenService;
        this.auditService = auditService;
        this.authenticationMetrics = authenticationMetrics;
        this.timingEqualizationHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public TokenResponse issueToken(String grantType, String clientId, String clientSecret) {
        if (!"client_credentials".equals(grantType)) {
            throw new BadRequestException("Unsupported grant_type: only client_credentials is supported");
        }
        ServiceClient client = parseClientId(clientId)
                .flatMap(serviceClientRepository::findById)
                .orElse(null);
        if (client == null) {
            passwordEncoder.matches(String.valueOf(clientSecret), timingEqualizationHash);
            throw new InvalidCredentialsException();
        }
        if (!client.isActive() || !passwordEncoder.matches(clientSecret, client.getSecretHash())) {
            throw new InvalidCredentialsException();
        }

        client.recordUsage();
        String token = jsonWebTokenService.generateServiceToken(client);
        auditService.record(null, "service:" + client.getName(), AuditActions.SERVICE_TOKEN_ISSUED, "ServiceClient",
                client.getId().toString(), AuditLog.Result.SUCCESS, null);
        authenticationMetrics.tokenIssued("service");
        return TokenResponse.bearer(token, jsonWebTokenService.getServiceTokenTtlSeconds(),
                String.join(" ", client.getScopes()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceClientResponse> list(String search, Pageable pageable) {
        Specification<ServiceClient> spec = ServiceClientSpecifications.search(search);
        return PageResponse.from(serviceClientRepository.findAll(spec, pageable), client -> toResponse(client, null));
    }

    @Transactional(readOnly = true)
    public ServiceClientResponse get(UUID id) {
        return toResponse(load(id), null);
    }

    @Transactional
    public ServiceClientResponse create(ServiceClientRequest request) {
        if (serviceClientRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A service client with this name already exists");
        }
        String rawSecret = TokenHasher.generateToken();
        ServiceClient client = ServiceClient.create(
                request.name().trim(), request.description(), passwordEncoder.encode(rawSecret),
                validateScopes(request.scopes()), request.active() == null || request.active());
        client = serviceClientRepository.save(client);
        auditService.record(null, null, AuditActions.SERVICE_CLIENT_CREATED, "ServiceClient",
                client.getId().toString(), AuditLog.Result.SUCCESS, Map.of("name", client.getName()));
        return toResponse(client, rawSecret);
    }

    @Transactional
    public ServiceClientResponse update(UUID id, ServiceClientRequest request) {
        ServiceClient client = load(id);
        if (!client.getName().equalsIgnoreCase(request.name())
                && serviceClientRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A service client with this name already exists");
        }
        Map<String, Object> before = new HashMap<>();
        before.put("name", client.getName());
        before.put("description", client.getDescription() == null ? "" : client.getDescription());
        before.put("scopes", List.copyOf(client.getScopes()));
        before.put("active", client.isActive());

        client.update(request.name().trim(), request.description(), validateScopes(request.scopes()), request.active());
        auditService.record(null, null, AuditActions.SERVICE_CLIENT_UPDATED, "ServiceClient", id.toString(),
                AuditLog.Result.SUCCESS, Map.of("before", before));
        return toResponse(client, null);
    }

    @Transactional
    public ServiceClientResponse rotateSecret(UUID id) {
        ServiceClient client = load(id);
        String rawSecret = TokenHasher.generateToken();
        client.rotateSecret(passwordEncoder.encode(rawSecret));
        auditService.record(null, null, AuditActions.SERVICE_CLIENT_SECRET_ROTATED, "ServiceClient", id.toString(),
                AuditLog.Result.SUCCESS, Map.of("name", client.getName()));
        return toResponse(client, rawSecret);
    }

    @Transactional
    public void delete(UUID id) {
        ServiceClient client = load(id);
        serviceClientRepository.delete(client);
        auditService.record(null, null, AuditActions.SERVICE_CLIENT_DELETED, "ServiceClient", id.toString(),
                AuditLog.Result.SUCCESS, Map.of("name", client.getName()));
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

    private List<String> validateScopes(Set<String> scopes) {
        if (scopes.isEmpty()) {
            return new ArrayList<>();
        }
        List<Permission> permissions = permissionRepository.findByNameIn(scopes);
        if (permissions.size() != scopes.size()) {
            Set<String> found = permissions.stream().map(Permission::getName).collect(Collectors.toSet());
            Set<String> missing = new HashSet<>(scopes);
            missing.removeAll(found);
            throw new BadRequestException("Unknown scopes: " + String.join(", ", missing));
        }
        return new ArrayList<>(new HashSet<>(scopes));
    }

    private java.util.Optional<UUID> parseClientId(String clientId) {
        try {
            return java.util.Optional.of(UUID.fromString(clientId));
        } catch (IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }

    private ServiceClient load(UUID id) {
        return serviceClientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceClient", id));
    }

    private ServiceClientResponse toResponse(ServiceClient client, String rawSecret) {
        return new ServiceClientResponse(
                client.getId(),
                client.getName(),
                client.getDescription(),
                List.copyOf(client.getScopes()),
                client.isActive(),
                client.getLastUsedAt(),
                client.getCreatedAt(),
                client.getUpdatedAt(),
                rawSecret);
    }
}
