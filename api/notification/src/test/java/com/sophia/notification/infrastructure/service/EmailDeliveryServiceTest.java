package com.sophia.notification.infrastructure.service;

import com.sophia.notification.domain.entity.EmailOutboxMessage;
import com.sophia.notification.domain.entity.NotificationChannelCodes;
import com.sophia.notification.domain.entity.NotificationDispatch;
import com.sophia.notification.domain.entity.enums.NotificationDispatchStatus;
import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
import com.sophia.notification.infrastructure.repository.NotificationDispatchRepository;
import com.sophia.notification.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    @Mock
    private EmailOutboxRepository outboxRepository;
    @Mock
    private NotificationDispatchRepository dispatchRepository;

    private EmailDeliveryService service;

    private final UUID messageId = UUID.randomUUID();
    private final UUID dispatchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmailDeliveryService(outboxRepository, dispatchRepository,
                TestFixtures.applicationProperties());
    }

    @Test
    void claimingCountsTheAttemptAndPushesTheNextOneOutOfReach() {
        EmailOutboxMessage message = pending(0);
        when(outboxRepository.findById(messageId)).thenReturn(Optional.of(message));

        EmailDeliveryService.ClaimedEmail claimed = service.claim(messageId).orElseThrow();

        assertThat(claimed.attempts()).isEqualTo(1);
        assertThat(claimed.recipient()).isEqualTo("ada@sophia.local");
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void anAlreadyDeliveredMessageIsNotClaimedAgain() {
        EmailOutboxMessage message = pending(1);
        message.markSent("[delivered]");
        when(outboxRepository.findById(messageId)).thenReturn(Optional.of(message));

        assertThat(service.claim(messageId)).isEmpty();
        assertThat(message.getAttempts()).isEqualTo(1);
    }

    @Test
    void aMessageThatVanishedIsNotClaimed() {
        when(outboxRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThat(service.claim(messageId)).isEmpty();
    }

    @Test
    void markingSentStampsTheMessageAndItsDispatch() {
        EmailOutboxMessage message = pending(1);
        NotificationDispatch dispatch = dispatch();
        when(outboxRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));

        service.markSent(claimed(1));

        assertThat(message.getPublishedAt()).isNotNull();
        assertThat(message.getLastError()).isNull();
        assertThat(dispatch.getStatus()).isEqualTo(NotificationDispatchStatus.SENT);
    }

    @Test
    void aDeliveredMessageStopsHoldingItsRenderedBody() {
        EmailOutboxMessage message = pending(1);
        when(outboxRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch()));

        service.markSent(claimed(1));

        assertThat(message.getBody()).doesNotContain("token");
        assertThat(message.getBody()).isEqualTo("[delivered]");
    }

    @Test
    void aFailedMessageKeepsItsBodyBecauseItStillNeedsToBeSent() {
        EmailOutboxMessage message = pending(1);
        lenient().when(outboxRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch()));

        service.markFailed(claimed(1), new RuntimeException("smtp refused"));

        assertThat(message.getBody()).isEqualTo("<p>Hi</p>");
    }

    @Test
    void aFailureShortOfTheLimitLeavesTheMessageRetryable() {
        NotificationDispatch dispatch = dispatch();
        lenient().when(outboxRepository.findById(messageId)).thenReturn(Optional.of(pending(2)));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));

        boolean exhausted = service.markFailed(claimed(2), new RuntimeException("smtp refused"));

        assertThat(exhausted).isFalse();
        assertThat(dispatch.getStatus()).isEqualTo(NotificationDispatchStatus.PENDING);
        assertThat(dispatch.getLastError()).isEqualTo("smtp refused");
        assertThat(dispatch.getAttempts()).isEqualTo(2);
    }

    @Test
    void theLastAllowedFailureGivesUpOnTheMessage() {
        EmailOutboxMessage message = pending(5);
        NotificationDispatch dispatch = dispatch();
        when(outboxRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));

        boolean exhausted = service.markFailed(claimed(5), new RuntimeException("mailbox unavailable"));

        assertThat(exhausted).isTrue();
        assertThat(message.getPublishedAt()).isNotNull();
        assertThat(message.getLastError()).isEqualTo("mailbox unavailable");
        assertThat(dispatch.getStatus()).isEqualTo(NotificationDispatchStatus.FAILED);
    }

    @Test
    void anOversizedErrorIsCutToFitTheColumn() {
        NotificationDispatch dispatch = dispatch();
        lenient().when(outboxRepository.findById(messageId)).thenReturn(Optional.of(pending(1)));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));

        service.markFailed(claimed(1), new RuntimeException("x".repeat(900)));

        assertThat(dispatch.getLastError()).hasSize(500);
    }

    @Test
    void anErrorWithoutAMessageStillRecordsSomething() {
        NotificationDispatch dispatch = dispatch();
        lenient().when(outboxRepository.findById(messageId)).thenReturn(Optional.of(pending(1)));
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));

        service.markFailed(claimed(1), new IllegalStateException());

        assertThat(dispatch.getLastError()).isEqualTo("IllegalStateException");
    }

    private EmailOutboxMessage pending(int attempts) {
        EmailOutboxMessage message = EmailOutboxMessage.create(dispatchId, "DOCUMENT_WORKSPACE_MENTION",
                "ada@sophia.local", "You were mentioned", "<p>Hi</p>", null, Instant.now().minusSeconds(1));
        for (int i = 0; i < attempts; i++) {
            message.claimAttempt(Duration.ZERO);
        }
        return message;
    }

    private NotificationDispatch dispatch() {
        return NotificationDispatch.create(null, UUID.randomUUID(), null, null, NotificationChannelCodes.EMAIL);
    }

    private EmailDeliveryService.ClaimedEmail claimed(int attempts) {
        return new EmailDeliveryService.ClaimedEmail(messageId, dispatchId, "DOCUMENT_WORKSPACE_MENTION",
                "ada@sophia.local", "You were mentioned", "<p>Hi</p>", attempts);
    }
}
