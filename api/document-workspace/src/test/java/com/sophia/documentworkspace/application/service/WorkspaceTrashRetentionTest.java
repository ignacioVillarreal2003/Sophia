package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAccessCache;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspacePreferencesRequest;
import com.sophia.documentworkspace.domain.entity.UserWorkspacePreferences;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.UserWorkspacePreferencesRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How long a deleted workspace is kept: the owner's choice, the deployment default otherwise, and what the job does with it. */
class WorkspaceTrashRetentionTest {

    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a@example.com", Set.of(), Set.of());

    private UserWorkspacePreferencesRepository repository;
    private ApplicationProperties properties;
    private UserWorkspacePreferencesService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserWorkspacePreferencesRepository.class);
        properties = mock(ApplicationProperties.class);
        when(properties.retention()).thenReturn(new ApplicationProperties.Retention(30, 30, 3));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new UserWorkspacePreferencesService(repository, properties);
    }

    @Test
    void usesTheDeploymentDefaultUntilThePersonChooses() {
        when(repository.findById(user.id())).thenReturn(Optional.empty());
        var response = service.get(user);
        assertThat(response.trashRetentionDays()).isEqualTo(30);
        assertThat(response.defaultTrashRetentionDays()).isEqualTo(30);
        assertThat(response.customized()).isFalse();
        assertThat(service.trashRetentionDays(user.id())).isEqualTo(30);
    }

    @Test
    void aChoiceIsSavedAndAppliedToTheOwner() {
        when(repository.findById(user.id())).thenReturn(Optional.empty());
        service.update(user, new UpdateWorkspacePreferencesRequest(7));
        verify(repository).save(any(UserWorkspacePreferences.class));

        when(repository.findById(user.id())).thenReturn(Optional.of(UserWorkspacePreferences.of(user.id(), 7)));
        assertThat(service.get(user).trashRetentionDays()).isEqualTo(7);
        assertThat(service.get(user).customized()).isTrue();
        assertThat(service.trashRetentionDays(user.id())).isEqualTo(7);
        assertThat(service.trashRetentionDays(UUID.randomUUID())).isEqualTo(30);
    }

    @Test
    void changingTheChoiceUpdatesTheSameRow() {
        UserWorkspacePreferences existing = UserWorkspacePreferences.of(user.id(), 7);
        when(repository.findById(user.id())).thenReturn(Optional.of(existing));
        service.update(user, new UpdateWorkspacePreferencesRequest(90));
        assertThat(existing.getTrashRetentionDays()).isEqualTo(90);
        verify(repository, never()).save(any());
    }

    // ---- the job -----------------------------------------------------------------------------------------------

    private static Workspace workspace(UUID owner) {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(UUID.randomUUID());
        when(workspace.getOwnerId()).thenReturn(owner);
        return workspace;
    }

    private WorkspacePurgeJob job(WorkspaceRepository workspaces, TrashService trash, WorkspaceNotifier notifier) {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(i -> {
            i.<Consumer<Object>>getArgument(0).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        return new WorkspacePurgeJob(workspaces, trash, notifier, mock(DomainEventPublisher.class),
                mock(WorkspaceAccessCache.class), properties, service, transactions);
    }

    @Test
    void theJobErasesWhatTheRepositoryReportsExpiredWithTheDefaultAsFallback() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        TrashService trash = mock(TrashService.class);
        Workspace expired = workspace(user.id());
        when(workspaces.findExpired(eq(30), anyInt())).thenReturn(List.of(expired)).thenReturn(List.of());
        when(workspaces.findById(expired.getId())).thenReturn(Optional.of(expired));

        job(workspaces, trash, mock(WorkspaceNotifier.class)).purgeExpired();

        verify(trash).purgeWorkspaceContent(expired.getId());
        verify(workspaces).delete(expired);
    }

    @Test
    void aFailureDoesNotLoopForeverAndTheRestStillRun() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        TrashService trash = mock(TrashService.class);
        Workspace bad = workspace(user.id());
        Workspace good = workspace(user.id());
        when(workspaces.findExpired(eq(30), anyInt())).thenReturn(List.of(bad, good));
        when(workspaces.findById(bad.getId())).thenReturn(Optional.of(bad));
        when(workspaces.findById(good.getId())).thenReturn(Optional.of(good));
        UUID badId = bad.getId();
        doAnswer(i -> {
            throw new IllegalStateException("storage down");
        }).when(trash).purgeWorkspaceContent(badId);

        job(workspaces, trash, mock(WorkspaceNotifier.class)).purgeExpired();

        verify(workspaces).delete(good);
        verify(workspaces, never()).delete(bad);
        verify(workspaces, times(1)).findExpired(anyInt(), anyInt()); // it stopped instead of asking again
    }

    @Test
    void theOwnerIsWarnedWithTheirOwnDeadline() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        WorkspaceNotifier notifier = mock(WorkspaceNotifier.class);
        Workspace due = workspace(user.id());
        when(due.isDeleted()).thenReturn(true);
        when(due.getName()).thenReturn("Proyecto");
        when(due.getDeletedAt()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        when(workspaces.findToWarn(30, 3)).thenReturn(List.of(due));
        when(workspaces.findById(due.getId())).thenReturn(Optional.of(due));
        when(repository.findById(user.id())).thenReturn(Optional.of(UserWorkspacePreferences.of(user.id(), 10)));

        job(workspaces, mock(TrashService.class), notifier).warnOwners();

        UUID dueId = due.getId();
        verify(notifier).notify(eq(user.id()), any(), any(), org.mockito.ArgumentMatchers.contains("2026-01-11"), eq(dueId));
        verify(due).markPurgeWarningSent();
    }
}
