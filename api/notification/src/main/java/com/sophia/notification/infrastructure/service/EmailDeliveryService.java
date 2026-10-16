package com.sophia.notification.infrastructure.service;

import com.sophia.notification.domain.config.ApplicationProperties;
import com.sophia.notification.domain.entity.EmailOutboxMessage;
import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
import com.sophia.notification.infrastructure.repository.NotificationDispatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private static final int MAX_ERROR_LENGTH = 500;
    private static final String DELIVERED_BODY = "[delivered]";

    private final EmailOutboxRepository outboxRepository;
    private final NotificationDispatchRepository dispatchRepository;
    private final ApplicationProperties properties;

    public record ClaimedEmail(UUID id, UUID dispatchId, String category, String recipient, String subject,
                               String body, int attempts) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedEmail> claim(UUID id) {
        EmailOutboxMessage message = outboxRepository.findById(id).orElse(null);
        if (message == null || message.getPublishedAt() != null) {
            return Optional.empty();
        }
        int attempts = message.claimAttempt(properties.email().baseBackoff());
        return Optional.of(new ClaimedEmail(message.getId(), message.getDispatchId(), message.getCategory(),
                message.getRecipient(), message.getSubject(), message.getBody(), attempts));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(ClaimedEmail email) {
        outboxRepository.findById(email.id()).ifPresent(message -> message.markSent(DELIVERED_BODY));
        if (email.dispatchId() == null) {
            return;
        }
        dispatchRepository.findById(email.dispatchId()).ifPresent(dispatch -> {
            dispatch.markSent();
            dispatchRepository.save(dispatch);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(ClaimedEmail email, Throwable error) {
        boolean exhausted = email.attempts() >= properties.email().maxAttempts();
        String reason = truncate(error);
        outboxRepository.findById(email.id()).ifPresent(message -> {
            if (exhausted) {
                message.discard(reason);
            } else {
                message.markFailed(reason);
            }
        });
        if (email.dispatchId() != null) {
            dispatchRepository.findById(email.dispatchId()).ifPresent(dispatch -> {
                if (exhausted) {
                    dispatch.markFailed(reason, email.attempts());
                } else {
                    dispatch.markAttemptFailed(reason, email.attempts());
                }
                dispatchRepository.save(dispatch);
            });
        }
        return exhausted;
    }

    private String truncate(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
