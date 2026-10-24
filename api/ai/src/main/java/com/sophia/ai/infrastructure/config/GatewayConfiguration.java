package com.sophia.ai.infrastructure.config;

import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.infrastructure.llm.GeminiGateway;
import com.sophia.ai.infrastructure.llm.ModelGateway;
import com.sophia.ai.infrastructure.llm.MockGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Configuration
public class GatewayConfiguration {

    @Bean
    public ModelGateway modelGateway(ApplicationProperties properties, Environment environment) {
        var ai = properties.ai();
        if (ai.isMock()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("AI_PROVIDER=mock returns canned answers and cannot run with the prod profile");
            }
            log.warn("AI provider is MOCK: answers are canned and nothing is sent to a model");
            return new MockGateway();
        }
        ModelGateway gateway = new GeminiGateway(ai);
        if (!gateway.configured()) {
            log.warn("GEMINI_API_KEY is not set: AI features will answer AI_NOT_CONFIGURED until it is");
        } else {
            log.info("AI provider: Gemini, model {}", ai.model());
        }
        return gateway;
    }

    /** Streaming generations run here, one virtual thread each, since they spend their life waiting on the model. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService generationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService heartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ai-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}
