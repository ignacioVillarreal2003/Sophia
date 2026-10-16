package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.domain.entity.AuditLog;
import com.sophia.documentworkspace.infrastructure.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit entries outside the transaction that produced them.
 *
 * <p>A separate bean on purpose: {@code @Async} and {@code REQUIRES_NEW} are
 * applied by a Spring proxy, and a proxy is only crossed on a call between
 * beans. {@code AuditService} reaches this method through several internal
 * overloads, so annotating it there would leave both silently inert — the audit
 * write would run on the caller thread and join the business transaction,
 * exactly what it must not do.
 *
 * <p>Failures are swallowed and logged: an audit entry that cannot be written
 * must never break the operation it was recording.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogPersister {

    private final AuditLogRepository auditLogRepository;

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AuditLog entry) {
        try {
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist audit entry action={}", entry.getAction(), e);
        }
    }
}
