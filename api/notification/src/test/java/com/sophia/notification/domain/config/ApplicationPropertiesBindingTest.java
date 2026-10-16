package com.sophia.notification.domain.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class PropertiesConfiguration {
    }

    @Test
    void theShippedConfigurationBindsAndPassesValidation() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ApplicationProperties properties = context.getBean(ApplicationProperties.class);

            assertThat(properties.commands().topic()).isEqualTo("sophia.notifications.commands");
            assertThat(properties.commands().deadLetterTopic()).isEqualTo("sophia.notifications.commands.dlt");
            assertThat(properties.commands().partitions()).isEqualTo(6);
            assertThat(properties.commands().concurrency()).isEqualTo(3);

            assertThat(properties.userEvents().topic()).isEqualTo("sophia.authentication.user-events");
            assertThat(properties.userEvents().deadLetterTopic())
                    .isEqualTo("sophia.authentication.user-events.dlt");
            assertThat(properties.userEvents().concurrency()).isEqualTo(2);

            assertThat(properties.email().backlogRefreshInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.stream().heartbeatInterval()).isEqualTo(Duration.ofSeconds(20));
            assertThat(properties.retention().readNotifications()).isEqualTo(Duration.ofDays(90));
        });
    }

    @Test
    void developmentDefaultsToASingleBrokerAndProductionToThree() {
        runner.run(context -> assertThat(context.getBean(ApplicationProperties.class).commands().replicas())
                .isEqualTo(1));

        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            ApplicationProperties properties = context.getBean(ApplicationProperties.class);
            assertThat(properties.commands().replicas()).isEqualTo(3);
            assertThat(properties.userEvents().replicas()).isEqualTo(3);
        });
    }

    @Test
    void anInvalidReplicationFactorStopsTheApplicationInsteadOfCreatingABadTopic() {
        runner.withPropertyValues("sophia.commands.replicas=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
