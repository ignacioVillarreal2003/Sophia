package com.sophia.documentworkspace.infrastructure.config;

import com.sophia.documentworkspace.infrastructure.service.NoOpVirusScanner;
import com.sophia.documentworkspace.infrastructure.service.VirusScanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VirusScanConfiguration {

    @Bean
    @ConditionalOnMissingBean(VirusScanner.class)
    public VirusScanner virusScanner() {
        return new NoOpVirusScanner();
    }
}
