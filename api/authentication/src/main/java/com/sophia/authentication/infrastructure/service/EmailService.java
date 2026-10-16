package com.sophia.authentication.infrastructure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.NotificationCommandOutboxMessage;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserPreferences;
import com.sophia.authentication.infrastructure.messaging.MessageCorrelation;
import com.sophia.authentication.infrastructure.messaging.NotificationCommandPayload;
import com.sophia.authentication.infrastructure.repository.NotificationCommandOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final String TEMPLATE_EMAIL_VERIFICATION = "security.email-verification";
    private static final String TEMPLATE_PASSWORD_RESET = "security.password-reset";
    private static final String TEMPLATE_EMAIL_CHANGE_CONFIRMATION = "security.email-change-confirmation";
    private static final String TEMPLATE_PASSWORD_CHANGED = "security.password-changed";

    private final NotificationCommandOutboxRepository outboxRepository;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void sendVerificationEmail(User user, String rawToken) {
        enqueue(user, user.getEmail(), TEMPLATE_EMAIL_VERIFICATION, Map.of(
                "name", nameOf(user),
                "token", rawToken,
                "ttlHours", String.valueOf(properties.security().emailVerificationTokenTtl().toHours())));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sendPasswordResetEmail(User user, String rawToken) {
        enqueue(user, user.getEmail(), TEMPLATE_PASSWORD_RESET, Map.of(
                "name", nameOf(user),
                "token", rawToken,
                "ttlMinutes", String.valueOf(properties.security().passwordResetTokenTtl().toMinutes())));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sendEmailChangeConfirmation(User user, String newEmail, String rawToken) {
        enqueue(user, newEmail, TEMPLATE_EMAIL_CHANGE_CONFIRMATION, Map.of(
                "name", nameOf(user),
                "token", rawToken));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sendPasswordChangedNotice(User user) {
        enqueue(user, user.getEmail(), TEMPLATE_PASSWORD_CHANGED, Map.of(
                "name", nameOf(user)));
    }

    void enqueue(User user, String to, String template, Map<String, String> variables) {
        if (!properties.notifications().enabled()) {
            log.info("Notifications disabled; skipping '{}' to {}", template, to);
            return;
        }
        NotificationCommandPayload payload = NotificationCommandPayload.securityEmail(
                user.getId(), to, user.getFullName(), localeOf(user), template, variables);
        outboxRepository.save(NotificationCommandOutboxMessage.create(
                routingKey(user, to), MessageCorrelation.current(), serialize(payload), Instant.now()));
    }

    private String routingKey(User user, String to) {
        UUID userId = user.getId();
        return userId == null ? to : userId.toString();
    }

    private String nameOf(User user) {
        return user.getFirstName() == null ? "" : user.getFirstName();
    }

    private String localeOf(User user) {
        UserPreferences preferences = user.getPreferences();
        return preferences == null ? null : preferences.getLocale();
    }

    private String serialize(NotificationCommandPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification command", e);
        }
    }
}
