package com.sophia.authentication.infrastructure.messaging;

import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserEventOutbox;
import com.sophia.authentication.domain.entity.UserPreferences;
import com.sophia.authentication.infrastructure.repository.UserEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final UserEventOutboxRepository outboxRepository;
    private final ApplicationProperties properties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UserEventType eventType, UUID userId, String email) {
        if (!properties.events().enabled()) {
            return;
        }
        outboxRepository.save(UserEventOutbox.create(
                eventType.name(), userId, email, null, null, null, MessageCorrelation.current(), Instant.now()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UserEventType eventType, User user) {
        if (!properties.events().enabled()) {
            return;
        }
        outboxRepository.save(UserEventOutbox.create(
                eventType.name(), user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                localeOf(user), MessageCorrelation.current(), Instant.now()));
    }

    private String localeOf(User user) {
        UserPreferences preferences = user.getPreferences();
        return preferences == null ? null : preferences.getLocale();
    }
}
