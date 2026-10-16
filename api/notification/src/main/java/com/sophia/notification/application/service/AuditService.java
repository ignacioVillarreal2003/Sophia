package com.sophia.notification.application.service;

import com.sophia.notification.api.filter.CorrelationIdFilter;
import com.sophia.notification.application.mapper.AuditLogMapper;
import com.sophia.notification.domain.dto.response.AuditLogResponse;
import com.sophia.notification.domain.dto.response.PageResponse;
import com.sophia.notification.domain.entity.AuditLog;
import com.sophia.notification.infrastructure.repository.AuditLogRepository;
import com.sophia.notification.infrastructure.repository.specification.AuditLogSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    public static final String READ_OTHER_INBOX = "READ_OTHER_INBOX";
    public static final String READ_OTHER_PREFERENCES = "READ_OTHER_PREFERENCES";
    public static final String UPDATE_OTHER_PREFERENCES = "UPDATE_OTHER_PREFERENCES";
    public static final String CREATE_CATEGORY = "CREATE_CATEGORY";
    public static final String UPDATE_CATEGORY = "UPDATE_CATEGORY";
    public static final String DELETE_CATEGORY = "DELETE_CATEGORY";
    public static final String REPLAY_DEAD_LETTERS = "REPLAY_DEAD_LETTERS";

    public static final String RESOURCE_TYPE_USER = "User";

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogEntryMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String userEmail, String action, String resourceType, String resourceId,
                       AuditLog.Result result, Map<String, Object> details) {
        try {
            auditLogRepository.save(AuditLog.of(userId, userEmail, action, resourceType, resourceId,
                    result,
                    MDC.get(CorrelationIdFilter.CLIENT_IP_MDC_KEY),
                    MDC.get(CorrelationIdFilter.USER_AGENT_MDC_KEY),
                    MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY),
                    details == null || details.isEmpty() ? null : details));
        } catch (RuntimeException e) {
            log.error("Could not write the audit entry {} by {} on {}:{}", action, userId, resourceType,
                    resourceId, e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(UUID userId, String action, String resourceType,
                                                      String resourceId, AuditLog.Result result,
                                                      String requestId, Instant from, Instant to,
                                                      Pageable pageable) {
        String normalized = action == null || action.isBlank() ? null : action.trim().toUpperCase();
        return PageResponse.from(
                auditLogRepository.findAll(
                        AuditLogSpecifications.search(userId, normalized, resourceType, resourceId, result,
                                requestId, from, to),
                        withDefaultSort(pageable)),
                auditLogEntryMapper::toResponse);
    }

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

    private Pageable withDefaultSort(Pageable pageable) {
        return pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
