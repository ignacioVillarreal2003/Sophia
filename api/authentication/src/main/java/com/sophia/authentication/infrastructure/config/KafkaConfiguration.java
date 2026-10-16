package com.sophia.authentication.infrastructure.config;

import com.sophia.authentication.domain.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@RequiredArgsConstructor
public class KafkaConfiguration {

    private final ApplicationProperties properties;

    @Bean
    public NewTopic userEventsTopic() {
        ApplicationProperties.Events events = properties.events();
        return TopicBuilder.name(events.userTopic())
                .partitions(events.partitions())
                .replicas(events.replicas())
                .build();
    }
}
