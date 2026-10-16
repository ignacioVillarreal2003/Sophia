package com.sophia.authentication.application.service;

import com.sophia.authentication.domain.dto.response.AuditLogResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.entity.AuditLog;
import com.sophia.authentication.api.filter.CorrelationIdFilter;
import com.sophia.authentication.application.mapper.AuditLogMapper;
import com.sophia.authentication.infrastructure.repository.AuditLogRepository;
import com.sophia.authentication.application.security.SecurityUtilities;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final AuditLogPersister auditLogPersister;

    public void record(UUID userId, String userEmail, String action, String resourceType, String resourceId,
                       AuditLog.Result result, Map<String, Object> details) {
        try {
            AuditLog entry = AuditLog.of(
                    userId != null ? userId : SecurityUtilities.getCurrentUserId().orElse(null),
                    userEmail != null ? userEmail : SecurityUtilities.getCurrentUserEmail().orElse(null),
                    action, resourceType, resourceId, result,
                    MDC.get(CorrelationIdFilter.CLIENT_IP_MDC_KEY),
                    MDC.get(CorrelationIdFilter.USER_AGENT_MDC_KEY),
                    MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY),
                    details);

            auditLogPersister.persist(entry);
        } catch (Exception e) {
            log.error("Failed to dispatch audit entry action={}", action, e);
        }
    }

    public void success(String action, UUID userId, String userEmail) {
        record(userId, userEmail, action, null, null, AuditLog.Result.SUCCESS, null);
    }

    public void failure(String action, String email, Map<String, Object> details) {
        record(null, email, action, null, null, AuditLog.Result.FAILURE, details);
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
    public PageResponse<AuditLogResponse> search(UUID userId,
                                                 String action,
                                                 String resourceType,
                                                 String resourceId,
                                                 AuditLog.Result result,
                                                 String requestId,
                                                 Instant from,
                                                 Instant to,
                                                 Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (action != null && !action.isBlank()) {
                String like = "%" + action.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(root.get("userEmail")), like)));
            }
            if (resourceType != null && !resourceType.isBlank()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType));
            }
            if (resourceId != null && !resourceId.isBlank()) {
                predicates.add(cb.equal(root.get("resourceId"), resourceId));
            }
            if (result != null) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (requestId != null && !requestId.isBlank()) {
                predicates.add(cb.equal(root.get("requestId"), requestId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.from(auditLogRepository.findAll(spec, pageable), auditLogMapper::toResponse);
    }
}
