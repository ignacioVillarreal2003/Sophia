package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceLimits;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLimitsRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Warns a workspace owner once when storage passes 80% of the quota and once when it is full. The level that was
 * already announced lives on the limits row, so the owner is not nagged on every upload, and it is lowered again
 * when usage drops or the quota grows so a later climb warns again.
 */
@Service
@RequiredArgsConstructor
public class StorageQuotaService {

    static final int WARN_PERCENT = 80;
    static final int FULL_PERCENT = 100;

    private final WorkspaceLimitsRepository limitsRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ArtifactRepository artifactRepository;
    private final WorkspaceNotifier notifier;

    /** Re-evaluates the alert level for the usage the workspace has now plus {@code additionalBytes}. */
    public void evaluate(UUID workspaceId, long additionalBytes) {
        WorkspaceLimits limits = limitsRepository.findById(workspaceId).orElse(null);
        if (limits == null || limits.getStorageQuotaMb() == null) {
            return;
        }
        long quotaBytes = limits.getStorageQuotaMb() * 1024L * 1024L;
        long used = artifactRepository.sumFileSizes(workspaceId) + Math.max(additionalBytes, 0);
        int level = level(used, quotaBytes);
        int previous = limits.getQuotaAlertLevel();
        if (level == previous) {
            return;
        }
        limits.recordQuotaAlert(level);
        if (level > previous) {
            announce(workspaceId, level, used, quotaBytes);
        }
    }

    static int level(long usedBytes, long quotaBytes) {
        if (quotaBytes <= 0) {
            return 0;
        }
        if (usedBytes >= quotaBytes) {
            return FULL_PERCENT;
        }
        return usedBytes * 100 >= quotaBytes * WARN_PERCENT ? WARN_PERCENT : 0;
    }

    private void announce(UUID workspaceId, int level, long used, long quotaBytes) {
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId).orElse(null);
        if (workspace == null) {
            return;
        }
        long percent = Math.min(100, used * 100 / quotaBytes);
        if (level >= FULL_PERCENT) {
            notifier.notify(workspace.getOwnerId(), null, "Storage full",
                    workspace.getName() + " has no storage left. Free some space or ask for more.", workspaceId);
        } else {
            notifier.notify(workspace.getOwnerId(), null, "Storage almost full",
                    workspace.getName() + " is using " + percent + "% of its storage.", workspaceId);
        }
    }
}
