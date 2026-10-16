package com.sophia.authentication.domain.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

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

            assertThat(properties.security().jwt().issuer()).isEqualTo("sophia-authentication");
            assertThat(properties.storage().bucket()).isEqualTo("sophia-avatars");
            assertThat(properties.monitoring().username()).isEqualTo("prometheus");
            assertThat(properties.admin().email()).isEqualTo("admin@sophia.local");
            assertThat(properties.events().userTopic()).isEqualTo("sophia.authentication.user-events");
        });
    }

    @Test
    void aBlankNestedFieldStopsTheApplicationInsteadOfBindingSilently() {
        runner.withPropertyValues("sophia.security.jwt.issuer=")
                .run(context -> assertThat(context).hasFailed());
    }
}
