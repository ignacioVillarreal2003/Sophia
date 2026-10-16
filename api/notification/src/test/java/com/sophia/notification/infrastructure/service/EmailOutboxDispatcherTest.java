package com.sophia.notification.infrastructure.service;

import com.sophia.notification.application.service.NotificationCategoryService;
import com.sophia.notification.domain.entity.NotificationCategoryView;
import com.sophia.notification.infrastructure.observability.NotificationMetrics;
import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
import com.sophia.notification.support.TestFixtures;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailOutboxDispatcherTest {

    @Mock
    private EmailOutboxRepository outboxRepository;
    @Mock
    private EmailDeliveryService deliveryService;
    @Mock
    private NotificationCategoryService categoryService;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private NotificationMetrics metrics;

    private CircuitBreaker circuitBreaker;
    private EmailOutboxDispatcher dispatcher;

    private final UUID messageId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .build());
        circuitBreaker = registry.circuitBreaker("mail");
        dispatcher = new EmailOutboxDispatcher(outboxRepository, deliveryService, categoryService, mailSender,
                TestFixtures.applicationProperties(), registry, metrics);

        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new java.util.Properties())));
        when(outboxRepository.findNextBatchIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(messageId));
        when(categoryService.findByCode("DOCUMENT_WORKSPACE_MENTION"))
                .thenReturn(new NotificationCategoryView("DOCUMENT_WORKSPACE_MENTION", "Mentions", null, false, true));
        when(categoryService.findByCode("AUTHENTICATION_SECURITY"))
                .thenReturn(new NotificationCategoryView("AUTHENTICATION_SECURITY", "Security", null, true, true));
    }

    @Test
    void aDeliveredEmailIsMarkedSentAndCounted() {
        when(deliveryService.claim(messageId)).thenReturn(Optional.of(claimed("DOCUMENT_WORKSPACE_MENTION")));

        dispatcher.dispatchPendingEmails();

        verify(mailSender).send(any(MimeMessage.class));
        verify(deliveryService).markSent(any());
        verify(metrics).emailSent();
    }

    @Test
    void bulkMailCarriesAnUnsubscribeHeaderAndAPlainTextAlternative() throws Exception {
        when(deliveryService.claim(messageId)).thenReturn(Optional.of(claimed("DOCUMENT_WORKSPACE_MENTION")));

        dispatcher.dispatchPendingEmails();

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getHeader("List-Unsubscribe")).isNotNull();
        assertThat(sent.getContent()).isInstanceOf(jakarta.mail.Multipart.class);
    }

    @Test
    void mandatoryMailCarriesNoUnsubscribeHeader() throws Exception {
        when(deliveryService.claim(messageId)).thenReturn(Optional.of(claimed("AUTHENTICATION_SECURITY")));

        dispatcher.dispatchPendingEmails();

        assertThat(captureSentMessage().getHeader("List-Unsubscribe")).isNull();
    }

    @Test
    void aFailedSendIsRecordedAgainstTheMessage() {
        when(deliveryService.claim(messageId)).thenReturn(Optional.of(claimed("DOCUMENT_WORKSPACE_MENTION")));
        when(deliveryService.markFailed(any(), any())).thenReturn(false);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        dispatcher.dispatchPendingEmails();

        verify(deliveryService).markFailed(any(), any());
        verify(deliveryService, never()).markSent(any());
        verify(metrics, never()).emailFailed();
    }

    @Test
    void runningOutOfAttemptsCountsAsAFailedEmail() {
        when(deliveryService.claim(messageId)).thenReturn(Optional.of(claimed("DOCUMENT_WORKSPACE_MENTION")));
        when(deliveryService.markFailed(any(), any())).thenReturn(true);
        doThrow(new MailSendException("mailbox gone")).when(mailSender).send(any(MimeMessage.class));

        dispatcher.dispatchPendingEmails();

        verify(metrics).emailFailed();
    }

    @Test
    void anOpenCircuitBreakerDefersTheBatchWithoutSpendingAnAttempt() {
        circuitBreaker.transitionToOpenState();

        dispatcher.dispatchPendingEmails();

        verify(deliveryService, never()).claim(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void aMessageClaimedByNobodyReleasesItsPermit() {
        when(deliveryService.claim(messageId)).thenReturn(Optional.empty());

        dispatcher.dispatchPendingEmails();

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void theEmailChannelCanBeTurnedOffEntirely() {
        dispatcher = new EmailOutboxDispatcher(outboxRepository, deliveryService, categoryService, mailSender,
                TestFixtures.applicationProperties(false),
                CircuitBreakerRegistry.ofDefaults(), metrics);

        dispatcher.dispatchPendingEmails();

        verify(outboxRepository, never()).findNextBatchIds(any(), any());
    }

    @Test
    void oneUnhappyMessageDoesNotCostTheRestOfTheBatch() {
        UUID second = UUID.randomUUID();
        when(outboxRepository.findNextBatchIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(messageId, second));
        when(deliveryService.claim(messageId)).thenThrow(new IllegalStateException("database hiccup"));
        when(deliveryService.claim(second)).thenReturn(Optional.of(claimed("DOCUMENT_WORKSPACE_MENTION")));

        dispatcher.dispatchPendingEmails();

        verify(deliveryService).markSent(any());
    }

    private MimeMessage captureSentMessage() {
        org.mockito.ArgumentCaptor<MimeMessage> captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private EmailDeliveryService.ClaimedEmail claimed(String category) {
        return new EmailDeliveryService.ClaimedEmail(messageId, UUID.randomUUID(), category,
                "ada@sophia.local", "You were mentioned",
                "<p>Hi Ada,</p><p><a href=\"https://sophia.local/x\">Open Sophia</a></p>", 1);
    }
}
