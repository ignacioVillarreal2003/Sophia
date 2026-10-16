package com.sophia.notification.application.service;

import com.sophia.notification.domain.entity.UserContact;
import com.sophia.notification.infrastructure.messaging.UserLifecycleEvent;
import com.sophia.notification.infrastructure.observability.NotificationMetrics;
import com.sophia.notification.infrastructure.repository.NotificationCategoryChannelPreferenceRepository;
import com.sophia.notification.infrastructure.repository.NotificationChannelPreferenceRepository;
import com.sophia.notification.infrastructure.repository.NotificationRepository;
import com.sophia.notification.infrastructure.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserContactProjectionService {

    private final UserContactRepository userContactRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationChannelPreferenceRepository channelPreferenceRepository;
    private final NotificationCategoryChannelPreferenceRepository categoryChannelPreferenceRepository;
    private final NotificationMetrics metrics;

    @Transactional
    public void apply(UserLifecycleEvent event) {
        if (event.eventType() == null || event.userId() == null) {
            log.warn("Ignoring a user lifecycle event with a missing type or userId (type={}, userId={})",
                    event.eventType(), event.userId());
            return;
        }
        switch (event.eventType()) {
            case USER_CREATED, USER_SYNC, USER_UPDATED -> upsert(event);
            case USER_EMAIL_CHANGED -> changeEmail(event);
            case USER_DELETED -> delete(event);
        }
        metrics.userEventApplied(event.eventType().name());
    }

    private void upsert(UserLifecycleEvent event) {
        UserContact contact = userContactRepository.findById(event.userId())
                .orElseGet(() -> UserContact.createFor(event.userId()));
        contact.applyContactUpdate(event.email(), event.firstName(), event.lastName(), event.locale());
        if (contact.getEmail() == null || contact.getEmail().isBlank()) {
            log.warn("Ignoring user lifecycle event {} for {}: no email available",
                    event.eventType(), event.userId());
            return;
        }
        userContactRepository.save(contact);
    }

    private void changeEmail(UserLifecycleEvent event) {
        if (event.email() == null || event.email().isBlank()) {
            log.warn("Ignoring USER_EMAIL_CHANGED for {}: event carries no email", event.userId());
            return;
        }
        UserContact contact = userContactRepository.findById(event.userId())
                .orElseGet(() -> UserContact.createFor(event.userId()));
        contact.updateEmail(event.email());
        userContactRepository.save(contact);
    }

    private void delete(UserLifecycleEvent event) {
        notificationRepository.deleteAllForRecipient(event.userId());
        categoryChannelPreferenceRepository.deleteAllForUser(event.userId());
        channelPreferenceRepository.deleteAllForUser(event.userId());
        userContactRepository.deleteById(event.userId());
    }
}
