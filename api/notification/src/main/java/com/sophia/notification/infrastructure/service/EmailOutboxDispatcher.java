package com.sophia.notification.infrastructure.service;

import com.sophia.notification.application.service.NotificationCategoryService;
import com.sophia.notification.application.service.PlainTextRenderer;
import com.sophia.notification.domain.config.ApplicationProperties;
import com.sophia.notification.infrastructure.observability.NotificationMetrics;
import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class EmailOutboxDispatcher {

    private static final int BATCH_SIZE = 50;

    private final EmailOutboxRepository outboxRepository;
    private final EmailDeliveryService deliveryService;
    private final NotificationCategoryService categoryService;
    private final JavaMailSender mailSender;
    private final ApplicationProperties properties;
    private final CircuitBreaker mailCircuitBreaker;
    private final NotificationMetrics metrics;

    public EmailOutboxDispatcher(EmailOutboxRepository outboxRepository,
                                 EmailDeliveryService deliveryService,
                                 NotificationCategoryService categoryService,
                                 JavaMailSender mailSender,
                                 ApplicationProperties properties,
                                 CircuitBreakerRegistry circuitBreakerRegistry,
                                 NotificationMetrics metrics) {
        this.outboxRepository = outboxRepository;
        this.deliveryService = deliveryService;
        this.categoryService = categoryService;
        this.mailSender = mailSender;
        this.properties = properties;
        this.mailCircuitBreaker = circuitBreakerRegistry.circuitBreaker("mail");
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${sophia.email.dispatch-interval}")
    @SchedulerLock(name = "email-outbox-dispatcher", lockAtMostFor = "PT9M", lockAtLeastFor = "PT5S")
    public void dispatchPendingEmails() {
        if (!properties.email().enabled()) {
            return;
        }
        List<UUID> due = outboxRepository.findNextBatchIds(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        for (UUID id : due) {
            try {
                if (!deliver(id)) {
                    return;
                }
            } catch (RuntimeException e) {
                log.error("Unexpected failure while dispatching the email {}", id, e);
            }
        }
    }

    private boolean deliver(UUID id) {
        if (!mailCircuitBreaker.tryAcquirePermission()) {
            log.warn("Mail circuit breaker is open; deferring the rest of the email outbox batch");
            return false;
        }
        EmailDeliveryService.ClaimedEmail email = deliveryService.claim(id).orElse(null);
        if (email == null) {
            mailCircuitBreaker.releasePermission();
            return true;
        }

        long startedAt = System.nanoTime();
        try {
            send(email);
        } catch (Throwable e) {
            mailCircuitBreaker.onError(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS, e);
            if (deliveryService.markFailed(email, e)) {
                metrics.emailFailed();
                log.error("Giving up on email {} to {} after {} attempts",
                        email.id(), email.recipient(), email.attempts(), e);
            } else {
                log.warn("Attempt {}/{} to send '{}' to {} failed: {}",
                        email.attempts(), properties.email().maxAttempts(), email.subject(),
                        email.recipient(), e.getMessage());
            }
            return true;
        }
        mailCircuitBreaker.onSuccess(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        deliveryService.markSent(email);
        metrics.emailSent();
        return true;
    }

    private void send(EmailDeliveryService.ClaimedEmail email) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.email().from());
        helper.setTo(email.recipient());
        helper.setSubject(email.subject());
        helper.setText(PlainTextRenderer.fromHtml(email.body()), email.body());
        if (isUnsubscribable(email.category())) {
            mimeMessage.setHeader("List-Unsubscribe", "<" + preferenceCentreUrl() + ">");
        }
        mailSender.send(mimeMessage);
    }

    private boolean isUnsubscribable(String category) {
        if (category == null || category.isBlank()) {
            return false;
        }
        try {
            return !categoryService.findByCode(category).mandatory();
        } catch (RuntimeException e) {
            log.debug("Could not resolve the category {} while addressing an email", category, e);
            return false;
        }
    }

    private String preferenceCentreUrl() {
        return UriComponentsBuilder.fromUriString(properties.frontend().baseUrl())
                .path(properties.frontend().notificationSettingsPath())
                .build()
                .encode()
                .toUriString();
    }
}
