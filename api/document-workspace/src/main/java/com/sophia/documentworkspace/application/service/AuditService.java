package com.sophia.documentworkspace.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.documentworkspace.api.filter.CorrelationIdFilter;
import com.sophia.documentworkspace.application.mapper.AuditLogMapper;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.response.AuditLogResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.entity.AuditLog;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.event.DomainEvent;
import com.sophia.documentworkspace.infrastructure.repository.AuditLogRepository;
import com.sophia.documentworkspace.infrastructure.repository.specification.AuditLogSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private static final Set<String> ENVELOPE_FIELDS = Set.of("eventId", "workspaceId", "actorId", "occurredAt");

    private final AuditLogRepository auditLogRepository;
    private final AuditLogPersister auditLogPersister;
    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Async
    @TransactionalEventListener
    public void onDomainEvent(DomainEvent event) {
        record(event.actorId(), null, event.eventType(), event.aggregateType(),
                event.aggregateId().toString(), AuditLog.Result.SUCCESS, snapshotOf(event),
                event.workspaceId());
    }

    private Map<String, Object> snapshotOf(DomainEvent event) {
        try {
            Map<String, Object> fields = objectMapper.convertValue(event,
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            fields.keySet().removeAll(ENVELOPE_FIELDS);
            return fields;
        } catch (IllegalArgumentException e) {
            log.warn("Could not serialize {} into an audit snapshot", event.eventType(), e);
            return Map.of();
        }
    }

    public void record(UUID userId, String userEmail, String action, String resourceType, String resourceId,
                       AuditLog.Result result, Map<String, Object> details) {
        record(userId, userEmail, action, resourceType, resourceId, result, details, null);
    }

    /**
     * Workspace-scoped variant: the workspace an action belongs to is folded into the
     * details payload so the audit trail can be filtered by tenant.
     */
    public void record(UUID userId, String userEmail, String action, String resourceType, String resourceId,
                       AuditLog.Result result, Map<String, Object> details, UUID workspaceId) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("workspaceId", workspaceId);
        if (details != null) {
            merged.putAll(details);
        }
        auditLogPersister.persist(AuditLog.of(userId, userEmail, action, resourceType, resourceId,
                result,
                MDC.get(CorrelationIdFilter.CLIENT_IP_MDC_KEY),
                MDC.get(CorrelationIdFilter.USER_AGENT_MDC_KEY),
                MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY),
                merged));
    }

    /** Builds a details map, dropping null values, for audit payloads. */
    public static Map<String, Object> details(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            Object value = keysAndValues[i + 1];
            if (value != null) {
                map.put((String) keysAndValues[i], value);
            }
        }
        return map;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> query(AuthenticatedUser user, UUID workspaceId,
                                                String action, UUID userId, String resourceType, String resourceId,
                                                AuditLog.Result result, String requestId, Instant from, Instant to,
                                                String text, Pageable pageable) {
        workspaceAuthorization.requirePermission(workspaceId, user, WorkspacePermission.VIEW_AUDIT);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("createdAt").descending());
        return PageResponse.from(
                auditLogRepository.findAll(AuditLogSpecifications.search(workspaceId, action, userId, resourceType,
                        resourceId, result, requestId, from, to, text), sorted),
                auditLogMapper::toResponse);
    }
}
