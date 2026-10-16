package com.sophia.authentication.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.sophia.authentication.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Security hardening")
class SecurityIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("protected endpoints reject anonymous requests with the standard error shape")
    void anonymousRejected() throws Exception {
        mockMvc.perform(get("/api/v1/authentication/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/authentication/me"));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("tampered tokens are rejected")
    void tamperedTokenRejected() throws Exception {
        JsonNode auth = registerVerifyAndLogin("tamper@test.local");
        String accessToken = auth.path("accessToken").asText();
        String tampered = accessToken.substring(0, accessToken.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/v1/authentication/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a plain USER cannot access administrative endpoints")
    void rbacDeniesRegularUser() throws Exception {
        JsonNode auth = registerVerifyAndLogin("plainuser@test.local");
        String accessToken = auth.path("accessToken").asText();

        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("every response carries a correlation id")
    void correlationIdPropagated() throws Exception {
        mockMvc.perform(get("/api/v1/authentication/me").header("X-Request-Id", "it-test-42"))
                .andExpect(header().string("X-Request-Id", "it-test-42"));

        mockMvc.perform(get("/api/v1/authentication/me"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("prometheus metrics require monitoring credentials while health stays public")
    void prometheusRequiresCredentials() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "wrong-password")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "prometheus")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a user can read their own sessions")
    void sessionsSelfService() throws Exception {
        JsonNode auth = registerVerifyAndLogin("sessions@test.local");
        String accessToken = auth.path("accessToken").asText();

        mockMvc.perform(get("/api/v1/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].current").value(true));
    }
}
