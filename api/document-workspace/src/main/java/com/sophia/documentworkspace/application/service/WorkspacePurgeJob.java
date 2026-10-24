package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.WorkspaceAccessCache;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.event.WorkspaceEvents;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Trashed workspaces live for as many days as their owner chose (the deployment default when they did not). A few days before the end the owner gets one warning; once the
 * time is up the workspace and everything in it is erased for good. Each workspace is handled in its own transaction
 * so one failure cannot hold back the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspacePurgeJob {

    private static final int BATCH = 25;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final WorkspaceRepository workspaceRepository;
    private final TrashService trashService;
    private final WorkspaceNotifier notifier;
    private final DomainEventPublisher eventPublisher;
    private final WorkspaceAccessCache accessCache;
    private final ApplicationProperties properties;
    private final UserWorkspacePreferencesService preferences;
    private final TransactionTemplate transactions;

    @Scheduled(cron = "0 30 4 * * *")
    @SchedulerLock(name = "workspace-purge", lockAtMostFor = "PT1H")
    public void run() {
        warnOwners();
        purgeExpired();
    }

    void warnOwners() {
        List<Workspace> due = workspaceRepository.findToWarn(preferences.defaultDays(),
                properties.retention().workspacePurgeWarningDays());
        for (Workspace candidate : due) {
            Duration keep = Duration.ofDays(preferences.trashRetentionDays(candidate.getOwnerId()));
            try {
                transactions.executeWithoutResult(status -> workspaceRepository.findById(candidate.getId())
                        .filter(w -> w.isDeleted() && w.getPurgeWarningSentAt() == null)
                        .ifPresent(w -> {
                            notifier.notify(w.getOwnerId(), null, "Workspace will be erased",
                                    w.getName() + " will be permanently deleted on "
                                            + DAY.format(w.getDeletedAt().plus(keep)) + ". Restore it before then to keep it.",
                                    w.getId());
                            w.markPurgeWarningSent();
                        }));
            } catch (RuntimeException e) {
                log.warn("Could not warn the owner of trashed workspace {}", candidate.getId(), e);
            }
        }
    }

    void purgeExpired() {
        int purged = 0;
        int failed = 0;
        List<Workspace> batch;
        do {
            batch = workspaceRepository.findExpired(preferences.defaultDays(), BATCH);
            for (Workspace workspace : batch) {
                UUID id = workspace.getId();
                try {
                    transactions.executeWithoutResult(status -> workspaceRepository.findById(id).ifPresent(w -> {
                        trashService.purgeWorkspaceContent(id);
                        workspaceRepository.delete(w);
                        eventPublisher.publish(WorkspaceEvents.WorkspacePurged.of(id, null));
                    }));
                    accessCache.evictWorkspace(id);
                    purged++;
                } catch (RuntimeException e) {
                    failed++;
                    log.error("Could not purge expired workspace {}", id, e);
                }
            }
            // Failed workspaces stay in the result set, so stop rather than loop on them forever.
        } while (!batch.isEmpty() && failed == 0);
        if (purged > 0) {
            log.info("Workspace purge erased {} trashed workspaces", purged);
        }
    }
}
