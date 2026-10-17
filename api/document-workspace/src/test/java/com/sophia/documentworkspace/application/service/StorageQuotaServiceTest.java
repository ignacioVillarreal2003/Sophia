package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceLimits;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceLimitsRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {

    private static final long MB = 1024L * 1024L;

    @Mock
    private WorkspaceLimitsRepository limitsRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private ArtifactRepository artifactRepository;
    @Mock
    private WorkspaceNotifier notifier;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private StorageQuotaService service() {
        return new StorageQuotaService(limitsRepository, workspaceRepository, artifactRepository, notifier);
    }

    private WorkspaceLimits limits(long quotaMb) {
        WorkspaceLimits limits = WorkspaceLimits.forWorkspace(workspaceId);
        limits.changeStorageQuota(quotaMb, null);
        return limits;
    }

    private void workspaceExists() {
        Workspace workspace = Workspace.create("Docs", null, null, null, "es", ownerId);
        when(workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)).thenReturn(Optional.of(workspace));
    }

    @Test
    void levelsFollowTheEightyAndHundredPercentThresholds() {
        assertThat(StorageQuotaService.level(79 * MB, 100 * MB)).isZero();
        assertThat(StorageQuotaService.level(80 * MB, 100 * MB)).isEqualTo(80);
        assertThat(StorageQuotaService.level(99 * MB, 100 * MB)).isEqualTo(80);
        assertThat(StorageQuotaService.level(100 * MB, 100 * MB)).isEqualTo(100);
        assertThat(StorageQuotaService.level(5, 0)).isZero();
    }

    @Test
    void crossingEightyPercentWarnsTheOwnerOnce() {
        WorkspaceLimits limits = limits(100);
        when(limitsRepository.findById(workspaceId)).thenReturn(Optional.of(limits));
        when(artifactRepository.sumFileSizes(workspaceId)).thenReturn(75 * MB);
        workspaceExists();

        service().evaluate(workspaceId, 10 * MB);
        service().evaluate(workspaceId, 12 * MB);

        assertThat(limits.getQuotaAlertLevel()).isEqualTo(80);
        verify(notifier).notify(eq(ownerId), any(), eq("Storage almost full"), anyString(), eq(workspaceId));
    }

    @Test
    void droppingBelowTheThresholdRearmsTheAlert() {
        WorkspaceLimits limits = limits(100);
        limits.recordQuotaAlert(80);
        when(limitsRepository.findById(workspaceId)).thenReturn(Optional.of(limits));
        when(artifactRepository.sumFileSizes(workspaceId)).thenReturn(10 * MB);

        service().evaluate(workspaceId, 0);

        assertThat(limits.getQuotaAlertLevel()).isZero();
        verify(notifier, never()).notify(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void aWorkspaceWithoutAQuotaNeverAlerts() {
        WorkspaceLimits limits = WorkspaceLimits.forWorkspace(workspaceId);
        when(limitsRepository.findById(workspaceId)).thenReturn(Optional.of(limits));

        service().evaluate(workspaceId, 1000 * MB);

        verify(notifier, never()).notify(any(), any(), anyString(), anyString(), any());
    }
}
