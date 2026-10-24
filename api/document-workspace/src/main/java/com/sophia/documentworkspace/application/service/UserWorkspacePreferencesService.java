package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.dto.request.UpdateWorkspacePreferencesRequest;
import com.sophia.documentworkspace.domain.dto.response.WorkspacePreferencesResponse;
import com.sophia.documentworkspace.domain.entity.UserWorkspacePreferences;
import com.sophia.documentworkspace.infrastructure.repository.UserWorkspacePreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * How long the person wants the workspaces they delete to stay in the trash. The deployment's own value is the
 * default; a choice applies to every workspace the person owns, including the ones already in the trash.
 */
@Service
@RequiredArgsConstructor
public class UserWorkspacePreferencesService {

    private final UserWorkspacePreferencesRepository repository;
    private final ApplicationProperties properties;

    @Transactional(readOnly = true)
    public WorkspacePreferencesResponse get(AuthenticatedUser user) {
        var chosen = repository.findById(user.id());
        return new WorkspacePreferencesResponse(
                chosen.map(UserWorkspacePreferences::getTrashRetentionDays).orElse(defaultDays()), defaultDays(),
                chosen.isPresent());
    }

    @Transactional
    public WorkspacePreferencesResponse update(AuthenticatedUser user, UpdateWorkspacePreferencesRequest request) {
        repository.findById(user.id()).ifPresentOrElse(
                existing -> existing.keepTrashFor(request.trashRetentionDays()),
                () -> repository.save(UserWorkspacePreferences.of(user.id(), request.trashRetentionDays())));
        return get(user);
    }

    /** Days a workspace deleted by this owner is kept. */
    @Transactional(readOnly = true)
    public int trashRetentionDays(UUID ownerId) {
        return repository.findById(ownerId).map(UserWorkspacePreferences::getTrashRetentionDays).orElse(defaultDays());
    }

    public int defaultDays() {
        return properties.retention().workspaceTrashDays();
    }
}
