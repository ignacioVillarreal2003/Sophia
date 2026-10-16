package com.sophia.documentworkspace.api.config;

import com.sophia.documentworkspace.api.security.JsonWebTokenAccessDeniedHandler;
import com.sophia.documentworkspace.api.security.JsonWebTokenAuthenticationEntryPoint;
import com.sophia.documentworkspace.api.security.JsonWebTokenUserAuthenticationConverter;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final ApplicationProperties properties;
    private final JsonWebTokenAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonWebTokenAccessDeniedHandler accessDeniedHandler;

    @Bean
    @Order(0)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorization -> authorization
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().hasRole("MONITORING"))
                .httpBasic(Customizer.withDefaults())
                .authenticationManager(monitoringAuthenticationManager());
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jsonWebTokenDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jsonWebTokenDecoder)
                                .jwtAuthenticationConverter(new JsonWebTokenUserAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private AuthenticationManager monitoringAuthenticationManager() {
        PasswordEncoder encoder = passwordEncoder();
        UserDetails monitoringUser = org.springframework.security.core.userdetails.User
                .withUsername(properties.monitoring().username())
                .password(encoder.encode(properties.monitoring().password()))
                .roles("MONITORING")
                .build();
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
                new InMemoryUserDetailsManager(monitoringUser));
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public JwtDecoder jsonWebTokenDecoder() {
        ApplicationProperties.Security.JsonWebToken jsonWebToken = properties.security().jwt();
        NimbusJwtDecoder decoder;
        if (jsonWebToken.hasJsonWebKeySetUri()) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jsonWebToken.jsonWebKeySetUri()).build();
        } else if (jsonWebToken.hasSecret()) {
            SecretKeySpec key = new SecretKeySpec(
                    jsonWebToken.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            decoder = NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        } else {
            throw new IllegalStateException(
                    "Configure sophia.security.jwt.json-web-key-set-uri (JWT_JSON_WEB_KEY_SET_URI) or "
                            + "sophia.security.jwt.secret (JWT_SECRET)");
        }
        if (jsonWebToken.issuer() != null && !jsonWebToken.issuer().isBlank()) {
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jsonWebToken.issuer()));
        }
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        ApplicationProperties.Security.Cors cors = properties.security().cors();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(cors.allowedOrigins());
        configuration.setAllowedMethods(cors.allowedMethods());
        configuration.setAllowedHeaders(cors.allowedHeaders());
        configuration.setExposedHeaders(cors.exposedHeaders());
        configuration.setAllowCredentials(cors.allowCredentials());
        configuration.setMaxAge(cors.maxAge());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
