package com.sophia.notification.infrastructure.service;

import com.sophia.notification.domain.config.ApplicationProperties;
import com.sophia.notification.domain.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterReplayService {

    public record ReplayResult(String topic, int replayed) {
    }

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> DEAD_LETTER_HEADERS = Set.of(
            KafkaHeaders.DLT_EXCEPTION_MESSAGE,
            KafkaHeaders.DLT_EXCEPTION_STACKTRACE,
            KafkaHeaders.DLT_EXCEPTION_FQCN,
            KafkaHeaders.DLT_ORIGINAL_TOPIC,
            KafkaHeaders.DLT_ORIGINAL_PARTITION,
            KafkaHeaders.DLT_ORIGINAL_OFFSET,
            KafkaHeaders.DLT_ORIGINAL_TIMESTAMP,
            KafkaHeaders.DLT_ORIGINAL_TIMESTAMP_TYPE,
            KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP);

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ApplicationProperties properties;

    public ReplayResult replay(String deadLetterTopic, int max) {
        String target = targetTopicFor(deadLetterTopic);
        if (max <= 0) {
            throw new BadRequestException("The number of records to replay must be positive");
        }

        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        overrides.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(max));

        int replayed = 0;
        try (Consumer<String, String> consumer = consumerFactory.createConsumer(
                "notification-dlt-replay-" + UUID.randomUUID(), null, null, overrides)) {
            consumer.subscribe(Set.of(deadLetterTopic));
            ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
            for (ConsumerRecord<String, String> record : records) {
                if (replayed >= max) {
                    break;
                }
                kafkaTemplate.send(rebuild(record, target));
                replayed++;
            }
            if (replayed > 0) {
                kafkaTemplate.flush();
                consumer.commitSync();
            }
        }
        log.info("Replayed {} records from {} onto {}", replayed, deadLetterTopic, target);
        return new ReplayResult(deadLetterTopic, replayed);
    }

    private org.springframework.messaging.Message<String> rebuild(ConsumerRecord<String, String> record,
                                                                   String target) {
        org.springframework.messaging.support.MessageBuilder<String> builder =
                org.springframework.messaging.support.MessageBuilder.withPayload(record.value())
                        .setHeader(KafkaHeaders.TOPIC, target);
        if (record.key() != null) {
            builder.setHeader(KafkaHeaders.KEY, record.key());
        }
        for (Header header : record.headers()) {
            if (!DEAD_LETTER_HEADERS.contains(header.key())) {
                builder.setHeader(header.key(), header.value());
            }
        }
        return builder.build();
    }

    private String targetTopicFor(String deadLetterTopic) {
        if (properties.commands().deadLetterTopic().equals(deadLetterTopic)) {
            return properties.commands().topic();
        }
        if (properties.userEvents().deadLetterTopic().equals(deadLetterTopic)) {
            return properties.userEvents().topic();
        }
        throw new BadRequestException("'%s' is not a dead letter topic owned by this service".formatted(
                deadLetterTopic));
    }
}
