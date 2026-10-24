package com.sophia.ai.application.service;

import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.dto.request.GenerationRequests.UpdateSettingsRequest;
import com.sophia.ai.domain.entity.AiSettings;
import com.sophia.ai.domain.exception.ForbiddenOperationException;
import com.sophia.ai.infrastructure.repository.AiSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The limits administrators can change at runtime. The row is created from the configured defaults on first use. */
@Service
@RequiredArgsConstructor
public class AiSettingsService {

    public static final String MANAGE_PERMISSION = "WORKSPACE_MANAGE";

    private final AiSettingsRepository repository;
    private final ApplicationProperties properties;

    @Transactional
    public AiSettings get() {
        return repository.findById(AiSettings.SINGLETON_ID).orElseGet(this::createDefaults);
    }

    private AiSettings createDefaults() {
        var defaults = properties.ai().defaults();
        try {
            return repository.saveAndFlush(AiSettings.of(defaults.enabled(), defaults.dailyTokensPerUser(),
                    defaults.dailyTokensPerWorkspace()));
        } catch (DataIntegrityViolationException raced) {
            // another instance created it first
            return repository.findById(AiSettings.SINGLETON_ID).orElseThrow(() -> raced);
        }
    }

    @Transactional
    public AiSettings update(AuthenticatedUser user, UpdateSettingsRequest request) {
        requireManage(user);
        AiSettings settings = get();
        settings.update(request.enabled(), request.dailyTokensPerUser(), request.dailyTokensPerWorkspace(), user.id());
        return settings;
    }

    public void requireManage(AuthenticatedUser user) {
        if (!user.hasPermission(MANAGE_PERMISSION)) {
            throw new ForbiddenOperationException("You do not have permission to manage the AI service");
        }
    }
}
