package com.sophia.documentworkspace.infrastructure.config;

import com.sophia.documentworkspace.domain.config.ApplicationProperties;
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
    public NewTopic domainEventsTopic() {
        return TopicBuilder.name(properties.events().topic())
                .partitions(6)
                .replicas(1)
                .build();
    }
}
