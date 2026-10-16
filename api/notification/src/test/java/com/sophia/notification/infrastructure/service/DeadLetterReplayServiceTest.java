package com.sophia.notification.infrastructure.service;

import com.sophia.notification.domain.exception.BadRequestException;
import com.sophia.notification.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeadLetterReplayServiceTest {

    @Mock
    private ConsumerFactory<String, String> consumerFactory;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private DeadLetterReplayService service;

    @BeforeEach
    void setUp() {
        service = new DeadLetterReplayService(consumerFactory, kafkaTemplate,
                TestFixtures.applicationProperties());
    }

    @Test
    void replayingATopicThisServiceDoesNotOwnIsRefused() {
        assertThatThrownBy(() -> service.replay("some.other.topic.dlt", 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a dead letter topic owned by this service");
        verifyNoInteractions(consumerFactory, kafkaTemplate);
    }

    @Test
    void replayingTheLiveTopicInsteadOfItsDeadLetterTopicIsRefused() {
        assertThatThrownBy(() -> service.replay("sophia.notifications.commands", 10))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(consumerFactory, kafkaTemplate);
    }

    @Test
    void aNonPositiveBatchIsRefusedBeforeTouchingKafka() {
        assertThatThrownBy(() -> service.replay("sophia.notifications.commands.dlt", 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be positive");
        verifyNoInteractions(consumerFactory, kafkaTemplate);
    }
}
