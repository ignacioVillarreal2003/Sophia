package com.sophia.authentication.infrastructure.messaging;

import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserEventOutbox;
import com.sophia.authentication.infrastructure.repository.UserEventOutboxRepository;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserEventPublisherTest {

    @Mock private UserEventOutboxRepository outboxRepository;

    private UserEventPublisher publisher(boolean enabled) {
        ApplicationProperties base = TestFixtures.appProperties();
        ApplicationProperties properties = new ApplicationProperties(base.security(), base.oauth2(),
                base.notifications(), base.storage(), base.monitoring(), base.admin(),
                new ApplicationProperties.Events(enabled, "sophia.authentication.user-events",
                        Duration.ofSeconds(5), 100, 10, 3, 1));
        return new UserEventPublisher(outboxRepository, properties);
    }

    @Test
    @DisplayName("does nothing while events are disabled")
    void skipsWhenDisabled() {
        publisher(false).publish(UserEventType.USER_CREATED, UUID.randomUUID(), "jane@test.local");

        verifyNoInteractions(outboxRepository);
    }

    @Test
    @DisplayName("writes an outbox row inside the caller transaction")
    void writesOutboxRow() {
        UUID userId = UUID.randomUUID();

        publisher(true).publish(UserEventType.USER_DELETED, userId, "jane@test.local");

        ArgumentCaptor<UserEventOutbox> entry = ArgumentCaptor.forClass(UserEventOutbox.class);
        verify(outboxRepository).save(entry.capture());
        assertThat(entry.getValue().getEventType()).isEqualTo("USER_DELETED");
        assertThat(entry.getValue().getUserId()).isEqualTo(userId);
        assertThat(entry.getValue().getEmail()).isEqualTo("jane@test.local");
        assertThat(entry.getValue().getOccurredAt()).isNotNull();
        assertThat(entry.getValue().getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("a user event carries the name and locale the projection needs")
    void carriesContactDetails() {
        User user = TestFixtures.user("jane@test.local", "hash");

        publisher(true).publish(UserEventType.USER_CREATED, user);

        ArgumentCaptor<UserEventOutbox> entry = ArgumentCaptor.forClass(UserEventOutbox.class);
        verify(outboxRepository).save(entry.capture());
        assertThat(entry.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(entry.getValue().getLastName()).isEqualTo("Doe");
        assertThat(entry.getValue().getLocale()).isEqualTo("en-US");
    }
}
