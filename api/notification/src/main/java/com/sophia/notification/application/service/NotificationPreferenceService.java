package com.sophia.notification.application.service;

import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.domain.dto.request.UpdateNotificationPreferencesRequest;
import com.sophia.notification.domain.dto.response.NotificationPreferencesResponse;
import com.sophia.notification.domain.entity.AuditLog;
import com.sophia.notification.domain.entity.NotificationCategoryChannelPreference;
import com.sophia.notification.domain.entity.NotificationCategoryChannelPreferenceId;
import com.sophia.notification.domain.entity.NotificationCategoryView;
import com.sophia.notification.domain.entity.NotificationChannel;
import com.sophia.notification.domain.entity.NotificationChannelCodes;
import com.sophia.notification.domain.entity.NotificationChannelPreference;
import com.sophia.notification.domain.entity.NotificationChannelPreferenceId;
import com.sophia.notification.domain.exception.BadRequestException;
import com.sophia.notification.domain.exception.ResourceNotFoundException;
import com.sophia.notification.infrastructure.repository.NotificationCategoryChannelPreferenceRepository;
import com.sophia.notification.infrastructure.repository.NotificationChannelPreferenceRepository;
import com.sophia.notification.infrastructure.repository.NotificationChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationChannelRepository channelRepository;
    private final NotificationChannelPreferenceRepository channelPreferenceRepository;
    private final NotificationCategoryChannelPreferenceRepository categoryChannelPreferenceRepository;
    private final NotificationCategoryService categoryService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getOwn(UUID userId) {
        return resolve(userId);
    }

    /** Reads another user's preferences on their behalf; the access itself is auditable. */
    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getUnrestricted(UUID userId, AuthenticatedUser actor) {
        NotificationPreferencesResponse preferences = resolve(userId);
        auditService.record(actor.id(), actor.email(), AuditService.READ_OTHER_PREFERENCES,
                AuditService.RESOURCE_TYPE_USER, userId.toString(), AuditLog.Result.SUCCESS, null);
        return preferences;
    }

    private NotificationPreferencesResponse resolve(UUID userId) {
        List<NotificationChannel> allChannels = channelRepository.findAll();
        Map<String, Boolean> channelOverrides = channelPreferenceRepository.findAllByIdUserId(userId).stream()
                .collect(Collectors.toMap(p -> p.getId().getChannel(), NotificationChannelPreference::isEnabled));

        Map<String, Boolean> channels = new LinkedHashMap<>();
        for (NotificationChannel channel : allChannels) {
            channels.put(channel.getCode(), channelOverrides.getOrDefault(channel.getCode(), true));
        }

        Map<String, Map<String, Boolean>> categoryOverrides = new LinkedHashMap<>();
        for (NotificationCategoryChannelPreference row : categoryChannelPreferenceRepository.findAllByIdUserId(userId)) {
            categoryOverrides
                    .computeIfAbsent(row.getId().getCategory(), key -> new LinkedHashMap<>())
                    .put(row.getId().getChannel(), row.isEnabled());
        }

        Map<String, Map<String, Boolean>> categories = new LinkedHashMap<>();
        for (NotificationCategoryView category : categoryService.findAll()) {
            if (category.mandatory()) {
                continue;
            }
            Map<String, Boolean> overrides = categoryOverrides.getOrDefault(category.code(), Map.of());
            Map<String, Boolean> resolved = new LinkedHashMap<>();
            for (NotificationChannel channel : allChannels) {
                resolved.put(channel.getCode(), overrides.getOrDefault(channel.getCode(), true));
            }
            categories.put(category.code(), resolved);
        }
        return new NotificationPreferencesResponse(channels, categories);
    }

    @Transactional
    public NotificationPreferencesResponse updateOwn(UUID userId, UpdateNotificationPreferencesRequest request) {
        return apply(userId, request, userId);
    }

    /** Updates another user's preferences on their behalf; the change itself is auditable. */
    @Transactional
    public NotificationPreferencesResponse updateUnrestricted(UUID userId,
                                                              UpdateNotificationPreferencesRequest request,
                                                              AuthenticatedUser actor) {
        NotificationPreferencesResponse updated = apply(userId, request, actor.id());
        auditService.record(actor.id(), actor.email(), AuditService.UPDATE_OTHER_PREFERENCES,
                AuditService.RESOURCE_TYPE_USER, userId.toString(), AuditLog.Result.SUCCESS,
                AuditService.details("channels", request.channels(), "categories", request.categories()));
        return updated;
    }

    private NotificationPreferencesResponse apply(UUID userId, UpdateNotificationPreferencesRequest request,
                                                   UUID updatedBy) {
        if (request.channels() != null) {
            for (Map.Entry<String, Boolean> entry : request.channels().entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                requireChannel(entry.getKey());
                NotificationChannelPreferenceId id = new NotificationChannelPreferenceId(userId, entry.getKey());
                NotificationChannelPreference preference = channelPreferenceRepository.findById(id)
                        .orElseGet(() -> NotificationChannelPreference.create(id, true));
                preference.update(entry.getValue(), updatedBy);
                channelPreferenceRepository.save(preference);
            }
        }

        if (request.categories() != null) {
            for (Map.Entry<String, Map<String, Boolean>> categoryEntry : request.categories().entrySet()) {
                String category = categoryEntry.getKey();
                if (categoryEntry.getValue() == null) {
                    continue;
                }
                if (requireCategory(category).mandatory()) {
                    throw new BadRequestException("Preferences for the %s category cannot be changed"
                            .formatted(category));
                }
                for (Map.Entry<String, Boolean> channelEntry : categoryEntry.getValue().entrySet()) {
                    if (channelEntry.getValue() == null) {
                        continue;
                    }
                    requireChannel(channelEntry.getKey());
                    NotificationCategoryChannelPreferenceId id =
                            new NotificationCategoryChannelPreferenceId(userId, category, channelEntry.getKey());
                    NotificationCategoryChannelPreference override = categoryChannelPreferenceRepository.findById(id)
                            .orElseGet(() -> NotificationCategoryChannelPreference.create(id, true));
                    override.update(channelEntry.getValue(), updatedBy);
                    categoryChannelPreferenceRepository.save(override);
                }
            }
        }
        return resolve(userId);
    }

    @Transactional(readOnly = true)
    public boolean channelAllowed(UUID userId, String category, String channel) {
        if (categoryService.findByCode(category).mandatory()) {
            return true;
        }
        NotificationChannelPreference channelPreference = channelPreferenceRepository
                .findById(new NotificationChannelPreferenceId(userId, channel)).orElse(null);
        if (channelPreference != null && !channelPreference.isEnabled()) {
            return false;
        }
        NotificationCategoryChannelPreference override = categoryChannelPreferenceRepository
                .findById(new NotificationCategoryChannelPreferenceId(userId, category, channel)).orElse(null);
        return override == null || override.isEnabled();
    }

    @Transactional(readOnly = true)
    public boolean inAppAllowed(UUID userId, String category) {
        return channelAllowed(userId, category, NotificationChannelCodes.IN_APP);
    }

    @Transactional(readOnly = true)
    public boolean emailAllowed(UUID userId, String category) {
        return channelAllowed(userId, category, NotificationChannelCodes.EMAIL);
    }

    private void requireChannel(String code) {
        if (!channelRepository.existsById(code)) {
            throw new BadRequestException("Unknown notification channel: " + code);
        }
    }

    private NotificationCategoryView requireCategory(String code) {
        try {
            return categoryService.findByCode(code);
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException("Unknown notification category: " + code);
        }
    }
}
