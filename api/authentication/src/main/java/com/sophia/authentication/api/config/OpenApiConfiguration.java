package com.sophia.authentication.api.config;

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
                        .title("Sophia Authentication API")
                        .description("""
                                Production-grade authentication and authorization service: \
                                JWT access tokens with rotating refresh tokens, Google OAuth2 login, \
                                RBAC with granular permissions, session management and auditing.
                                
                                Authenticate via `POST /api/v1/authentication/login` and send the access token as \
                                `Authorization: Bearer <token>`.""")
                        .version("1.0.0")
                        .contact(new Contact().name("Sophia Platform Team")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token issued by the login endpoints")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
