package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.domain.event.DomainEvent;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Keeps {@code workspaces.last_activity_at} fresh: any committed domain event of a workspace counts as activity,
 * except housekeeping that nobody did on purpose. The update is throttled so a busy workspace is written once a minute.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceActivityTracker {

    static final Duration THROTTLE = Duration.ofMinutes(1);
    private static final Set<String> IGNORED = Set.of("workspace.purged", "workspace.deleted", "file.preview-generated");

    private final WorkspaceRepository workspaceRepository;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDomainEvent(DomainEvent event) {
        if (event.workspaceId() == null || IGNORED.contains(event.eventType())) {
            return;
        }
        try {
            Instant now = Instant.now();
            workspaceRepository.touchActivity(event.workspaceId(), now, now.minus(THROTTLE));
        } catch (Exception e) {
            log.warn("Could not record activity for workspace {}", event.workspaceId(), e);
        }
    }
}
