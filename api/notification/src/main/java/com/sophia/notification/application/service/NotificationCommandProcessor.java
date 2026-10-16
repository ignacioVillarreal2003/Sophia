package com.sophia.notification.application.service;

import com.sophia.notification.application.mapper.NotificationMapper;
import com.sophia.notification.domain.config.ApplicationProperties;
import com.sophia.notification.domain.dto.response.NotificationResponse;
import com.sophia.notification.domain.entity.EmailOutboxMessage;
import com.sophia.notification.domain.entity.Notification;
import com.sophia.notification.domain.entity.NotificationAttributes;
import com.sophia.notification.domain.entity.NotificationChannelCodes;
import com.sophia.notification.domain.entity.NotificationDispatch;
import com.sophia.notification.domain.entity.ProcessedMessage;
import com.sophia.notification.domain.entity.UserContact;
import com.sophia.notification.domain.entity.enums.NotificationSeverity;
import com.sophia.notification.domain.exception.InvalidNotificationCommandException;
import com.sophia.notification.domain.exception.ResourceNotFoundException;
import com.sophia.notification.domain.messaging.NotificationCommand;
import com.sophia.notification.infrastructure.observability.NotificationMetrics;
import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
import com.sophia.notification.infrastructure.repository.NotificationDispatchRepository;
import com.sophia.notification.infrastructure.repository.NotificationRepository;
import com.sophia.notification.infrastructure.repository.ProcessedMessageRepository;
import com.sophia.notification.infrastructure.repository.UserContactRepository;
import com.sophia.notification.infrastructure.security.ratelimit.RedisRateLimiter;
import com.sophia.notification.infrastructure.service.ServerSentEventRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sophia.notification.infrastructure.messaging.MessageCorrelation;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCommandProcessor {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_BODY_LENGTH = 2000;
    private static final int MAX_TYPE_LENGTH = 40;
    private static final int MAX_GROUP_KEY_LENGTH = 200;
    private static final int MAX_TARGET_TYPE_LENGTH = 40;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_SUBJECT_LENGTH = 255;

    private final NotificationRepository notificationRepository;
    private final NotificationDispatchRepository dispatchRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final UserContactRepository userContactRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationCategoryService categoryService;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final ServerSentEventRegistry serverSentEventRegistry;
    private final RedisRateLimiter rateLimiter;
    private final NotificationMetrics metrics;
    private final ApplicationProperties properties;
    private final NotificationMapper notificationMapper;

    @Transactional
    public void process(NotificationCommand command) {
        validate(command);
        if (processedMessageRepository.existsById(command.messageId())) {
            metrics.commandDuplicate(command.category());
            log.debug("Skipping already processed notification command {}", command.messageId());
            return;
        }
        processedMessageRepository.save(ProcessedMessage.of(command.messageId()));

        UUID userId = command.recipient().userId();
        UserContact contact = userId == null
                ? null
                : userContactRepository.findById(userId).orElse(null);

        Instant deliverAt = deliveryInstant(command);
        UUID notificationId = null;
        if (command.inApp() != null && preferenceService.inAppAllowed(userId, command.category())) {
            notificationId = deliverInApp(command, userId, deliverAt);
        }
        if (command.email() != null
                && (userId == null || preferenceService.emailAllowed(userId, command.category()))) {
            deliverEmail(command, contact, notificationId, deliverAt);
        }
        metrics.commandProcessed(command.category());
    }

    private Instant deliveryInstant(NotificationCommand command) {
        Instant scheduledAt = command.scheduledAt();
        return scheduledAt == null || !scheduledAt.isAfter(Instant.now()) ? null : scheduledAt;
    }

    private UUID deliverInApp(NotificationCommand command, UUID userId, Instant deliverAt) {
        NotificationCommand.InAppMessage inApp = command.inApp();
        Notification notification = notificationRepository.save(Notification.of(
                userId,
                command.actorId(),
                command.category(),
                inApp.type() == null || inApp.type().isBlank() ? command.category() : inApp.type(),
                inApp.title(),
                inApp.body(),
                inApp.data(),
                toAttributes(command),
                deliverAt));

        NotificationDispatch dispatch = NotificationDispatch.create(
                notification.getId(), command.messageId(), MessageCorrelation.current(), userId,
                NotificationChannelCodes.IN_APP);
        dispatch.markSent();
        dispatchRepository.save(dispatch);

        metrics.inAppCreated(command.category());
        if (deliverAt == null) {
            pushAfterCommit(userId, notification);
        }
        return notification.getId();
    }

    private void deliverEmail(NotificationCommand command, UserContact contact, UUID notificationId,
                              Instant deliverAt) {
        String address = command.recipient().email() != null && !command.recipient().email().isBlank()
                ? command.recipient().email()
                : contact == null ? null : contact.getEmail();
        if (address == null || address.isBlank()) {
            metrics.emailSkipped("unknown_recipient");
            log.warn("Dropping email for command {}: no recipient address available", command.messageId());
            return;
        }
        if (!properties.email().enabled()) {
            metrics.emailSkipped("channel_disabled");
            log.info("Email channel disabled; skipping email for command {}", command.messageId());
            return;
        }
        if (!categoryService.findByCode(command.category()).mandatory()
                && !rateLimiter.tryConsume("email:" + address,
                properties.email().perRecipientLimit(),
                properties.email().perRecipientWindow()).allowed()) {
            metrics.emailSkipped("recipient_rate_limited");
            metrics.rateLimited("email");
            log.warn("Dropping email for command {}: recipient over the per-address rate limit",
                    command.messageId());
            return;
        }
        String displayName = command.recipient().displayName() != null
                && !command.recipient().displayName().isBlank()
                ? command.recipient().displayName()
                : contact == null ? address : contact.displayName();
        EmailTemplateRenderer.RenderedEmail rendered =
                emailTemplateRenderer.render(command, displayName, localeOf(command, contact));

        NotificationDispatch dispatch = dispatchRepository.save(NotificationDispatch.create(
                notificationId, command.messageId(), MessageCorrelation.current(),
                command.recipient().userId(), NotificationChannelCodes.EMAIL));
        emailOutboxRepository.save(EmailOutboxMessage.create(
                dispatch.getId(), command.category(), address, truncateSubject(rendered.subject()),
                rendered.htmlBody(), MessageCorrelation.current(),
                deliverAt == null ? Instant.now() : deliverAt));
        metrics.emailQueued(command.category());
    }

    private Locale localeOf(NotificationCommand command, UserContact contact) {
        String tag = command.recipient().locale() != null && !command.recipient().locale().isBlank()
                ? command.recipient().locale()
                : contact == null ? null : contact.getLocale();
        return tag == null || tag.isBlank() ? null : Locale.forLanguageTag(tag);
    }

    private NotificationAttributes toAttributes(NotificationCommand command) {
        NotificationCommand.Attributes attributes = command.attributes();
        if (attributes == null) {
            return NotificationAttributes.defaults();
        }
        return new NotificationAttributes(parseSeverity(attributes.severity()), attributes.priority(),
                attributes.groupKey(), attributes.targetType(), attributes.targetId(), attributes.expiresAt());
    }

    private NotificationSeverity parseSeverity(String severity) {
        return severity == null || severity.isBlank() ? null : NotificationSeverity.valueOf(severity);
    }

    private void validate(NotificationCommand command) {
        if (command.schemaVersionOrCurrent() != NotificationCommand.CURRENT_SCHEMA_VERSION) {
            throw new InvalidNotificationCommandException(
                    "Unsupported notification command schemaVersion %d; this service speaks version %d"
                            .formatted(command.schemaVersionOrCurrent(),
                                    NotificationCommand.CURRENT_SCHEMA_VERSION));
        }
        if (command.messageId() == null) {
            throw new InvalidNotificationCommandException("Notification command requires a messageId");
        }
        if (command.category() == null || command.category().isBlank()) {
            throw new InvalidNotificationCommandException("Notification command requires a category");
        }
        try {
            categoryService.findByCode(command.category());
        } catch (ResourceNotFoundException e) {
            throw new InvalidNotificationCommandException(
                    "Unknown notification category: " + command.category());
        }
        if (command.recipient() == null) {
            throw new InvalidNotificationCommandException("Notification command requires a recipient");
        }
        if (command.inApp() == null && command.email() == null) {
            throw new InvalidNotificationCommandException(
                    "Notification command requires at least one of inApp or email");
        }
        if (command.inApp() != null) {
            if (command.recipient().userId() == null) {
                throw new InvalidNotificationCommandException(
                        "In-application notifications require a recipient userId");
            }
            if (command.inApp().title() == null || command.inApp().title().isBlank()) {
                throw new InvalidNotificationCommandException(
                        "In-application notifications require a title");
            }
            requireLength("inApp.title", command.inApp().title(), MAX_TITLE_LENGTH);
            requireLength("inApp.body", command.inApp().body(), MAX_BODY_LENGTH);
            requireLength("inApp.type", command.inApp().type(), MAX_TYPE_LENGTH);
        }
        if (command.email() != null && command.recipient().userId() == null
                && (command.recipient().email() == null || command.recipient().email().isBlank())) {
            throw new InvalidNotificationCommandException(
                    "Email notifications require a recipient userId or an explicit email address");
        }
        requireLength("recipient.email", command.recipient().email(), MAX_EMAIL_LENGTH);
        validateAttributes(command.attributes());
    }

    private void validateAttributes(NotificationCommand.Attributes attributes) {
        if (attributes == null) {
            return;
        }
        String severity = attributes.severity();
        if (severity != null && !severity.isBlank() && !NotificationSeverity.ALL.contains(severity)) {
            throw new InvalidNotificationCommandException(
                    "Unsupported notification severity '%s'; expected one of %s"
                            .formatted(severity, NotificationSeverity.ALL));
        }
        requireLength("attributes.groupKey", attributes.groupKey(), MAX_GROUP_KEY_LENGTH);
        requireLength("attributes.targetType", attributes.targetType(), MAX_TARGET_TYPE_LENGTH);
    }

    private void requireLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new InvalidNotificationCommandException(
                    "%s is %d characters long; the maximum is %d".formatted(field, value.length(), max));
        }
    }

    private String truncateSubject(String subject) {
        return subject != null && subject.length() > MAX_SUBJECT_LENGTH
                ? subject.substring(0, MAX_SUBJECT_LENGTH)
                : subject;
    }

    /**
     * The payload is built inside the callback on purpose: columns filled in
     * at insert time, such as the creation timestamp, are only populated once
     * the transaction has flushed.
     */
    private void pushAfterCommit(UUID recipientId, Notification notification) {
        Runnable push = () ->
                serverSentEventRegistry.publish(recipientId, "notification",
                        notificationMapper.toResponse(notification));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }
}
