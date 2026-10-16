package com.sophia.authentication.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.NotificationCommandOutboxMessage;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.infrastructure.repository.NotificationCommandOutboxRepository;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private NotificationCommandOutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final User user = TestFixtures.user("jane@test.local", "hash");

    private EmailService emailService(boolean notificationsEnabled) {
        ApplicationProperties base = TestFixtures.appProperties();
        ApplicationProperties properties = new ApplicationProperties(base.security(), base.oauth2(),
                new ApplicationProperties.Notifications(notificationsEnabled,
                        "sophia.notifications.commands", Duration.ofSeconds(5), 100, 10),
                base.storage(), base.monitoring(), base.admin(), base.events());
        return new EmailService(outboxRepository, properties, objectMapper);
    }

    private JsonNode enqueuedPayload() throws Exception {
        ArgumentCaptor<NotificationCommandOutboxMessage> message =
                ArgumentCaptor.forClass(NotificationCommandOutboxMessage.class);
        verify(outboxRepository).save(message.capture());
        return objectMapper.readTree(message.getValue().getPayload());
    }

    @Test
    @DisplayName("does nothing when notifications are disabled")
    void skipsWhenDisabled() {
        emailService(false).sendPasswordChangedNotice(user);

        verifyNoInteractions(outboxRepository);
    }

    @Test
    @DisplayName("enqueues a templated security email command")
    void enqueuesTemplatedCommand() throws Exception {
        emailService(true).sendVerificationEmail(user, "raw-token-1");

        JsonNode payload = enqueuedPayload();
        assertThat(payload.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(payload.path("source").asText()).isEqualTo("authentication");
        assertThat(payload.path("messageId").asText()).isNotBlank();
        assertThat(payload.path("occurredAt").isNull()).isFalse();
        assertThat(payload.path("category").asText()).isEqualTo("AUTHENTICATION_SECURITY");
        assertThat(payload.path("email").path("template").asText()).isEqualTo("security.email-verification");
        assertThat(payload.path("email").path("variables").path("token").asText()).isEqualTo("raw-token-1");
        assertThat(payload.path("email").path("variables").path("ttlHours").asText()).isEqualTo("24");
    }

    @Test
    @DisplayName("the recipient carries everything the notification service needs to route and localise")
    void recipientCarriesRoutingDetails() throws Exception {
        emailService(true).sendVerificationEmail(user, "raw-token-1");

        JsonNode recipient = enqueuedPayload().path("recipient");
        assertThat(recipient.path("userId").asText()).isEqualTo(user.getId().toString());
        assertThat(recipient.path("email").asText()).isEqualTo("jane@test.local");
        assertThat(recipient.path("displayName").asText()).isEqualTo("Jane Doe");
        assertThat(recipient.path("locale").asText()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("commands are keyed by user so one recipient keeps its ordering")
    void commandsAreKeyedByUser() {
        emailService(true).sendVerificationEmail(user, "raw-token-1");

        ArgumentCaptor<NotificationCommandOutboxMessage> message =
                ArgumentCaptor.forClass(NotificationCommandOutboxMessage.class);
        verify(outboxRepository).save(message.capture());
        assertThat(message.getValue().getRecipientKey()).isEqualTo(user.getId().toString());
        assertThat(message.getValue().getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("an email change confirmation goes to the new address, not the current one")
    void emailChangeGoesToTheNewAddress() throws Exception {
        emailService(true).sendEmailChangeConfirmation(user, "new@test.local", "raw-token-3");

        JsonNode payload = enqueuedPayload();
        assertThat(payload.path("recipient").path("email").asText()).isEqualTo("new@test.local");
        assertThat(payload.path("recipient").path("userId").asText()).isEqualTo(user.getId().toString());
        assertThat(payload.path("email").path("template").asText())
                .isEqualTo("security.email-change-confirmation");
    }

    @Test
    @DisplayName("password reset command carries the reset token time to live")
    void passwordResetCommand() throws Exception {
        emailService(true).sendPasswordResetEmail(user, "raw-token-2");

        JsonNode payload = enqueuedPayload();
        assertThat(payload.path("email").path("template").asText()).isEqualTo("security.password-reset");
        assertThat(payload.path("email").path("variables").path("ttlMinutes").asText()).isEqualTo("30");
    }

    @Test
    @DisplayName("all email kinds enqueue through the same path")
    void allEmailKindsEnqueue() {
        EmailService service = emailService(true);

        service.sendVerificationEmail(user, "raw-token-1");
        service.sendPasswordResetEmail(user, "raw-token-2");
        service.sendEmailChangeConfirmation(user, "new@test.local", "raw-token-3");
        service.sendPasswordChangedNotice(user);

        verify(outboxRepository, times(4))
                .save(org.mockito.ArgumentMatchers.any(NotificationCommandOutboxMessage.class));
    }
}
