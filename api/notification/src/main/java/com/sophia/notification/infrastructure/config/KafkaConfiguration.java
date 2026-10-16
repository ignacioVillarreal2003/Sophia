package com.sophia.notification.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sophia.notification.domain.config.ApplicationProperties;
import com.sophia.notification.domain.exception.InvalidNotificationCommandException;
import com.sophia.notification.infrastructure.observability.NotificationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConfiguration {

    private final ApplicationProperties properties;
    private final ObjectProvider<NotificationMetrics> metrics;

    @Bean
    public NewTopic notificationCommandsTopic() {
        return TopicBuilder.name(properties.commands().topic())
                .partitions(properties.commands().partitions())
                .replicas(properties.commands().replicas())
                .build();
    }

    @Bean
    public NewTopic notificationCommandsDeadLetterTopic() {
        return TopicBuilder.name(properties.commands().deadLetterTopic())
                .partitions(1)
                .replicas(properties.commands().replicas())
                .build();
    }

    @Bean
    public NewTopic userEventsDeadLetterTopic() {
        return TopicBuilder.name(properties.userEvents().deadLetterTopic())
                .partitions(1)
                .replicas(properties.userEvents().replicas())
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> commandsListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(properties.commands().concurrency());
        factory.setCommonErrorHandler(errorHandler(
                kafkaTemplate, properties.commands().deadLetterTopic(), "notification-commands",
                InvalidNotificationCommandException.class));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> userEventsListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(properties.userEvents().concurrency());
        factory.setCommonErrorHandler(errorHandler(
                kafkaTemplate, properties.userEvents().deadLetterTopic(), "user-events"));
        return factory;
    }

    @SafeVarargs
    private DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                             String deadLetterTopic,
                                             String source,
                                             Class<? extends Exception>... notRetryable) {
        DeadLetterPublishingRecoverer publisher = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(deadLetterTopic, -1));

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(10000L);
        backOff.setMaxElapsedTime(60000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            log.error("Routing {}-{}@{} to the dead letter topic {}",
                    record.topic(), record.partition(), record.offset(), deadLetterTopic, exception);
            metrics.getObject().deadLettered(source);
            publisher.accept(record, exception);
        }, backOff);
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        errorHandler.addNotRetryableExceptions(notRetryable);
        return errorHandler;
    }
}
