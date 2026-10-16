package com.sophia.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.notification.application.service.UserContactProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserLifecycleEventConsumer {

    private final UserContactProjectionService projectionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${sophia.user-events.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "userEventsListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        MessageCorrelation.put(MessageCorrelation.from(record));
        try {
            UserLifecycleEvent event;
            try {
                event = objectMapper.readValue(record.value(), UserLifecycleEvent.class);
            } catch (JsonProcessingException e) {
                log.error("Skipping an unparseable user lifecycle event ({} bytes)", record.value().length(), e);
                return;
            }
            projectionService.apply(event);
        } finally {
            MessageCorrelation.clear();
        }
    }
}
