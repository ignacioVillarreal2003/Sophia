package com.sophia.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.notification.application.service.NotificationCategoryService;
import com.sophia.notification.application.service.NotificationCommandProcessor;
import com.sophia.notification.domain.entity.NotificationCategoryView;
import com.sophia.notification.domain.exception.InvalidNotificationCommandException;
import com.sophia.notification.domain.messaging.NotificationCommand;
import com.sophia.notification.infrastructure.observability.NotificationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationCommandConsumer {

    private static final String UNKNOWN_CATEGORY = "unknown";

    private final NotificationCommandProcessor processor;
    private final NotificationCategoryService categoryService;
    private final ObjectMapper objectMapper;
    private final NotificationMetrics metrics;

    @KafkaListener(
            topics = "${sophia.commands.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "commandsListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        MessageCorrelation.put(MessageCorrelation.from(record));
        try {
            handle(record.value());
        } finally {
            MessageCorrelation.clear();
        }
    }

    private void handle(String payload) {
        NotificationCommand command;
        try {
            command = objectMapper.readValue(payload, NotificationCommand.class);
        } catch (JsonProcessingException e) {
            metrics.commandRejected(UNKNOWN_CATEGORY);
            throw new InvalidNotificationCommandException("Unparseable notification command", e);
        }
        try {
            processor.process(command);
        } catch (InvalidNotificationCommandException e) {
            metrics.commandRejected(knownCategoryOrUnknown(command.category()));
            throw e;
        }
    }

    private String knownCategoryOrUnknown(String category) {
        if (category == null || category.isBlank()) {
            return UNKNOWN_CATEGORY;
        }
        try {
            boolean known = categoryService.findAll().stream()
                    .map(NotificationCategoryView::code)
                    .anyMatch(category::equals);
            return known ? category : UNKNOWN_CATEGORY;
        } catch (RuntimeException e) {
            log.debug("Could not read the category catalogue to tag a rejected command", e);
            return UNKNOWN_CATEGORY;
        }
    }
}
