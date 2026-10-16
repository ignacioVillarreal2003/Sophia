package com.sophia.notification.application.service;

import com.sophia.notification.application.mapper.NotificationMapper;
import com.sophia.notification.domain.entity.EmailOutboxMessage;
import com.sophia.notification.domain.entity.Notification;
import com.sophia.notification.domain.entity.NotificationCategoryView;
import com.sophia.notification.domain.entity.UserContact;
import com.sophia.notification.domain.entity.enums.NotificationSeverity;
import com.sophia.notification.domain.exception.InvalidNotificationCommandException;
import com.sophia.notification.domain.messaging.NotificationCommand;
import com.sophia.notification.infrastructure.observability.NotificationMetrics;
import com.sophia.notification.infrastructure.repository.EmailOutboxRepository;
import com.sophia.notification.infrastructure.repository.NotificationDispatchRepository;
import com.sophia.notification.infrastructure.repository.NotificationRepository;
import com.sophia.notification.infrastructure.repository.ProcessedMessageRepository;
import com.sophia.notification.infrastructure.repository.UserContactRepository;
import com.sophia.notification.infrastructure.service.ServerSentEventRegistry;
import com.sophia.notification.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCommandProcessorTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationDispatchRepository dispatchRepository;
    @Mock
    private ProcessedMessageRepository processedMessageRepository;
    @Mock
    private UserContactRepository userContactRepository;
    @Mock
    private EmailOutboxRepository emailOutboxRepository;
    @Mock
    private NotificationPreferenceService preferenceService;
    @Mock
    private NotificationCategoryService categoryService;
    @Mock
    private ServerSentEventRegistry serverSentEventRegistry;
    @Mock
    private com.sophia.notification.infrastructure.security.ratelimit.RedisRateLimiter rateLimiter;
    @Mock
    private NotificationMetrics metrics;

    private NotificationCommandProcessor processor;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new NotificationCommandProcessor(
                notificationRepository,
                dispatchRepository,
                processedMessageRepository,
                userContactRepository,
                emailOutboxRepository,
                preferenceService,
                categoryService,
                new EmailTemplateRenderer(TestFixtures.applicationProperties(), categoryService,
                        TestFixtures.emailMessages()),
                serverSentEventRegistry,
                rateLimiter,
                metrics,
                TestFixtures.applicationProperties(),
                Mappers.getMapper(NotificationMapper.class));
        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(categoryService.findByCode(anyString()))
                .thenAnswer(invocation -> new NotificationCategoryView(
                        invocation.getArgument(0), invocation.getArgument(0), null, false, false));
        lenient().when(rateLimiter.tryConsume(anyString(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(new com.sophia.notification.infrastructure.security.ratelimit.RedisRateLimiter.Result(true, 1, 0));
        lenient().when(dispatchRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void duplicateCommandsAreSkipped() {
        NotificationCommand command = inAppCommand();
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(true);

        processor.process(command);

        verify(notificationRepository, never()).save(any());
        verify(metrics).commandDuplicate("DOCUMENT_WORKSPACE_MENTION");
    }

    @Test
    void inAppCommandPersistsNotificationAndPushes() {
        NotificationCommand command = inAppCommand();
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(userContactRepository.findById(userId)).thenReturn(Optional.empty());
        when(preferenceService.inAppAllowed(userId, "DOCUMENT_WORKSPACE_MENTION")).thenReturn(true);

        processor.process(command);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo(userId);
        assertThat(captor.getValue().getTitle()).isEqualTo("You were mentioned");
        verify(serverSentEventRegistry).publish(eq(userId), eq("notification"), any());
        verify(metrics).commandProcessed("DOCUMENT_WORKSPACE_MENTION");
    }

    @Test
    void inAppCommandRespectsPreferences() {
        NotificationCommand command = inAppCommand();
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(userContactRepository.findById(userId)).thenReturn(Optional.empty());
        when(preferenceService.inAppAllowed(userId, "DOCUMENT_WORKSPACE_MENTION")).thenReturn(false);

        processor.process(command);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void emailCommandResolvesAddressFromContactProjection() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "DOCUMENT_WORKSPACE_MENTION", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                null,
                new NotificationCommand.EmailMessage("generic.notification",
                        Map.of("title", "Hello", "body", "World"), null, null));
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);
        UserContact contact = UserContact.createFor(userId);
        contact.applyContactUpdate("ada@sophia.local", "Ada", "Lovelace", null);
        when(userContactRepository.findById(userId)).thenReturn(Optional.of(contact));
        when(preferenceService.emailAllowed(userId, "DOCUMENT_WORKSPACE_MENTION")).thenReturn(true);

        processor.process(command);

        ArgumentCaptor<EmailOutboxMessage> captor = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(emailOutboxRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipient()).isEqualTo("ada@sophia.local");
        assertThat(captor.getValue().getBody()).contains("Hi Ada Lovelace,");
        verify(metrics).emailQueued("DOCUMENT_WORKSPACE_MENTION");
    }

    @Test
    void emailCommandWithoutResolvableAddressIsDropped() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "DOCUMENT_WORKSPACE_MENTION", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                null,
                new NotificationCommand.EmailMessage("generic.notification",
                        Map.of("title", "Hello"), null, null));
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(userContactRepository.findById(userId)).thenReturn(Optional.empty());
        when(preferenceService.emailAllowed(userId, "DOCUMENT_WORKSPACE_MENTION")).thenReturn(true);

        processor.process(command);

        verify(emailOutboxRepository, never()).save(any());
        verify(metrics).emailSkipped("unknown_recipient");
    }

    @Test
    void emailToExplicitAddressBypassesUserPreferences() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "DOCUMENT_WORKSPACE_MENTION", null, null,
                new NotificationCommand.Recipient(null, "invitee@sophia.local", null, null),
                null,
                new NotificationCommand.EmailMessage("document-workspace.invitation",
                        Map.of("workspaceName", "Docs", "token", "invite-token"), null, null));
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);

        processor.process(command);

        ArgumentCaptor<EmailOutboxMessage> captor = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(emailOutboxRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipient()).isEqualTo("invitee@sophia.local");
    }

    @Test
    void commandWithoutChannelsIsRejected() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "SYSTEM", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                null, null);

        assertThatThrownBy(() -> processor.process(command))
                .isInstanceOf(InvalidNotificationCommandException.class);
    }

    @Test
    void commandWithoutMessageIdIsRejected() {
        NotificationCommand command = new NotificationCommand(
                null, null, Instant.now(), null, "SYSTEM", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                new NotificationCommand.InAppMessage("SYSTEM", "Title", null, null), null);

        assertThatThrownBy(() -> processor.process(command))
                .isInstanceOf(InvalidNotificationCommandException.class);
    }

    @Test
    void inAppCommandWithoutUserIdIsRejected() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "SYSTEM", null, null,
                new NotificationCommand.Recipient(null, "someone@sophia.local", null, null),
                new NotificationCommand.InAppMessage("SYSTEM", "Title", null, null), null);

        assertThatThrownBy(() -> processor.process(command))
                .isInstanceOf(InvalidNotificationCommandException.class);
    }

    @Test
    void aSeverityTheSchemaWouldRejectIsRejectedHere() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "SYSTEM", null,
                new NotificationCommand.Attributes("CRITICAL", null, null, null, null, null),
                new NotificationCommand.Recipient(userId, null, null, null),
                new NotificationCommand.InAppMessage("SYSTEM", "Title", null, null), null);

        assertThatThrownBy(() -> processor.process(command))
                .isInstanceOf(InvalidNotificationCommandException.class)
                .hasMessageContaining("CRITICAL");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void everySeverityTheSchemaAllowsIsAccepted() {
        when(processedMessageRepository.existsById(any())).thenReturn(false);
        when(preferenceService.inAppAllowed(eq(userId), anyString())).thenReturn(true);

        for (String severity : NotificationSeverity.ALL) {
            processor.process(new NotificationCommand(
                    null, UUID.randomUUID(), Instant.now(), null, "SYSTEM", null,
                    new NotificationCommand.Attributes(severity, null, null, null, null, null),
                    new NotificationCommand.Recipient(userId, null, null, null),
                    new NotificationCommand.InAppMessage("SYSTEM", "Title", null, null), null));
        }

        verify(notificationRepository, times(NotificationSeverity.ALL.size())).save(any());
    }

    @Test
    void aTitleTooLongForItsColumnIsRejectedBeforeTheInsert() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "SYSTEM", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                new NotificationCommand.InAppMessage("SYSTEM", "T".repeat(256), null, null), null);

        assertThatThrownBy(() -> processor.process(command))
                .isInstanceOf(InvalidNotificationCommandException.class)
                .hasMessageContaining("inApp.title");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void aBodyTooLongForItsColumnIsRejectedBeforeTheInsert() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "SYSTEM", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                new NotificationCommand.InAppMessage("SYSTEM", "Title", "B".repeat(2001), null), null);

        assertThatThrownBy(() -> processor.process(command))
                .isInstanceOf(InvalidNotificationCommandException.class)
                .hasMessageContaining("inApp.body");
    }

    @Test
    void aGroupKeyTooLongForItsColumnIsRejectedBeforeTheInsert() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "SYSTEM", null,
                new NotificationCommand.Attributes(null, null, "g".repeat(201), null, null, null),
                new NotificationCommand.Recipient(userId, null, null, null),
                new NotificationCommand.InAppMessage("SYSTEM", "Title", null, null), null);

        assertThatThrownBy(() -> processor.process(command))
                .isInstanceOf(InvalidNotificationCommandException.class)
                .hasMessageContaining("attributes.groupKey");
    }

    @Test
    void aRenderedSubjectIsCutToFitItsColumn() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "DOCUMENT_WORKSPACE_MENTION", null, null,
                new NotificationCommand.Recipient(null, "invitee@sophia.local", null, null),
                null,
                new NotificationCommand.EmailMessage("generic.notification",
                        Map.of("title", "T".repeat(400)), null, null));
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);

        processor.process(command);

        ArgumentCaptor<EmailOutboxMessage> captor = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(emailOutboxRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).hasSize(255);
    }

    @Test
    void theQueuedEmailCarriesItsCategory() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "AUTHENTICATION_SECURITY", null, null,
                new NotificationCommand.Recipient(null, "ada@sophia.local", null, null),
                null,
                new NotificationCommand.EmailMessage("security.password-changed", Map.of(), null, null));
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);

        processor.process(command);

        ArgumentCaptor<EmailOutboxMessage> captor = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(emailOutboxRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("AUTHENTICATION_SECURITY");
    }

    @Test
    void aCommandFromANewerContractIsRejectedRatherThanMisread() {
        NotificationCommand command = new NotificationCommand(
                2, UUID.randomUUID(), Instant.now(), null, "SYSTEM", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                new NotificationCommand.InAppMessage("SYSTEM", "Title", null, null), null);

        assertThatThrownBy(() -> processor.process(command))
                .isInstanceOf(InvalidNotificationCommandException.class)
                .hasMessageContaining("schemaVersion");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void aCommandWithoutASchemaVersionIsTreatedAsTheCurrentOne() {
        when(processedMessageRepository.existsById(any())).thenReturn(false);
        when(preferenceService.inAppAllowed(eq(userId), anyString())).thenReturn(true);

        processor.process(inAppCommand());

        verify(notificationRepository).save(any());
    }

    @Test
    void aNotificationScheduledForLaterIsStoredButNotPushed() {
        Instant scheduledAt = Instant.now().plusSeconds(3600);
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), scheduledAt, "SYSTEM", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                new NotificationCommand.InAppMessage("SYSTEM", "Later", null, null), null);
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(preferenceService.inAppAllowed(userId, "SYSTEM")).thenReturn(true);

        processor.process(command);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getScheduledAt()).isEqualTo(scheduledAt);
        verify(serverSentEventRegistry, never()).publish(any(), any(), any());
    }

    @Test
    void aScheduledEmailWaitsInTheOutboxUntilItsTime() {
        Instant scheduledAt = Instant.now().plusSeconds(3600);
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), scheduledAt, "SYSTEM", null, null,
                new NotificationCommand.Recipient(null, "ada@sophia.local", null, null),
                null,
                new NotificationCommand.EmailMessage("generic.notification",
                        Map.of("title", "Later"), null, null));
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);

        processor.process(command);

        ArgumentCaptor<EmailOutboxMessage> captor = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(emailOutboxRepository).save(captor.capture());
        assertThat(captor.getValue().getNextAttemptAt()).isEqualTo(scheduledAt);
    }

    @Test
    void aScheduleAlreadyInThePastIsDeliveredImmediately() {
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), Instant.now().minusSeconds(60), "SYSTEM", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                new NotificationCommand.InAppMessage("SYSTEM", "Now", null, null), null);
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);
        when(preferenceService.inAppAllowed(userId, "SYSTEM")).thenReturn(true);

        processor.process(command);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getScheduledAt()).isNull();
        verify(serverSentEventRegistry).publish(eq(userId), eq("notification"), any());
    }

    @Test
    void anAddressOverItsEmailRateLimitIsDropped() {
        when(rateLimiter.tryConsume(eq("email:flooded@sophia.local"), org.mockito.ArgumentMatchers.anyInt(),
                any())).thenReturn(
                new com.sophia.notification.infrastructure.security.ratelimit.RedisRateLimiter.Result(false, 0, 60));
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "SYSTEM", null, null,
                new NotificationCommand.Recipient(null, "flooded@sophia.local", null, null),
                null,
                new NotificationCommand.EmailMessage("generic.notification",
                        Map.of("title", "Spam"), null, null));
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);

        processor.process(command);

        verify(emailOutboxRepository, never()).save(any());
        verify(metrics).emailSkipped("recipient_rate_limited");
    }

    @Test
    void aMandatoryCategoryIgnoresTheEmailRateLimit() {
        when(categoryService.findByCode("AUTHENTICATION_SECURITY")).thenReturn(
                new NotificationCategoryView("AUTHENTICATION_SECURITY", "Security", null, true, true));
        NotificationCommand command = new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "AUTHENTICATION_SECURITY", null, null,
                new NotificationCommand.Recipient(null, "flooded@sophia.local", null, null),
                null,
                new NotificationCommand.EmailMessage("security.password-changed", Map.of(), null, null));
        when(processedMessageRepository.existsById(command.messageId())).thenReturn(false);

        processor.process(command);

        verify(emailOutboxRepository).save(any());
        verify(rateLimiter, never()).tryConsume(anyString(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }

    private NotificationCommand inAppCommand() {
        return new NotificationCommand(
                null, UUID.randomUUID(), Instant.now(), null, "DOCUMENT_WORKSPACE_MENTION", null, null,
                new NotificationCommand.Recipient(userId, null, null, null),
                new NotificationCommand.InAppMessage("MENTION", "You were mentioned", "In a comment",
                        Map.of("workspaceId", UUID.randomUUID().toString())),
                null);
    }
}
