package com.sophia.authentication.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.sophia.authentication.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Machine-to-machine authentication (JSON Web Key Set + client_credentials)")
class ServiceAuthenticationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("the JSON Web Key Set endpoint publishes the RS256 public key without authentication")
    void jsonWebKeySetIsPublic() throws Exception {
        mockMvc.perform(get("/.well-known/json-web-key-set.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].e").exists());
    }

    @Test
    @DisplayName("client_credentials grant issues a scoped token that RBAC honors")
    void clientCredentialsFlow() throws Exception {
        String adminToken = loginAsAdmin("m2m-admin@test.local");

        // Register a service client with a read-only scope
        MvcResult created = mockMvc.perform(post("/api/v1/service-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "billing-service",
                                "description", "Billing backend",
                                "scopes", Set.of("USER_READ")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.clientSecret").exists())
                .andReturn();
        JsonNode client = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        String clientId = client.path("id").asText();
        String clientSecret = client.path("clientSecret").asText();

        // The secret is never returned again
        mockMvc.perform(get("/api/v1/service-clients/{id}", clientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());

        // Standard OAuth2 client_credentials grant (form-encoded)
        MvcResult tokenResult = mockMvc.perform(post("/api/v1/authentication/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", clientId)
                        .param("client_secret", clientSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber())
                .andExpect(jsonPath("$.scope").value("USER_READ"))
                .andReturn();
        String serviceToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .path("access_token").asText();
        assertThat(serviceToken).isNotBlank();

        // The scope grants read access...
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isOk());

        // ...but nothing beyond it
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "X", "lastName", "Y",
                                "email", "x@test.local", "password", PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("wrong client secrets and unknown grants are rejected")
    void invalidClientCredentialsRejected() throws Exception {
        String adminToken = loginAsAdmin("m2m-admin2@test.local");
        MvcResult created = mockMvc.perform(post("/api/v1/service-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "reporting-service",
                                "scopes", Set.of("USER_READ")))))
                .andExpect(status().isCreated())
                .andReturn();
        String clientId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/authentication/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", clientId)
                        .param("client_secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/authentication/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", clientId)
                        .param("client_secret", "whatever"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rotating the secret invalidates the previous one")
    void secretRotation() throws Exception {
        String adminToken = loginAsAdmin("m2m-admin3@test.local");
        MvcResult created = mockMvc.perform(post("/api/v1/service-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "rotating-service",
                                "scopes", Set.of("USER_READ")))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode client = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        String clientId = client.path("id").asText();
        String oldSecret = client.path("clientSecret").asText();

        MvcResult rotated = mockMvc.perform(post("/api/v1/service-clients/{id}/rotate-secret", clientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String newSecret = objectMapper.readTree(rotated.getResponse().getContentAsString())
                .path("data").path("clientSecret").asText();
        assertThat(newSecret).isNotBlank().isNotEqualTo(oldSecret);

        mockMvc.perform(post("/api/v1/authentication/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", clientId)
                        .param("client_secret", oldSecret))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/authentication/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", clientId)
                        .param("client_secret", newSecret))
                .andExpect(status().isOk());
    }
}
