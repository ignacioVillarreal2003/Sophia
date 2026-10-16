package com.sophia.documentworkspace.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sophia Document Workspace API")
                        .description("""
                                Document management and realtime collaboration service: workspaces, \
                                folders, block-based documents, tables, files, templates, comments, \
                                versioning and search. Notifications are delivered by the Sophia \
                                Notification Service, fed through its Kafka command topic. \
                                Authentication is delegated to the Sophia Authentication Service \
                                (JWT bearer tokens).""")
                        .version("1.0.0")
                        .contact(new Contact().name("Sophia Platform")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
