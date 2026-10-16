package com.sophia.authentication.integration;

import com.sophia.authentication.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Idempotency-Key handling on POST endpoints")
class IdempotencyIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("retrying a POST with the same Idempotency-Key replays the first response")
    void retryReplaysFirstResponse() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Idem", "lastName", "Potent",
                "email", "idempotent@test.local", "password", PASSWORD));

        MvcResult first = mockMvc.perform(post("/api/v1/authentication/register")
                        .header("Idempotency-Key", "reg-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        // Without the key this retry would fail with 409 (duplicate email);
        // with it, the cached 201 response is replayed byte for byte.
        MvcResult second = mockMvc.perform(post("/api/v1/authentication/register")
                        .header("Idempotency-Key", "reg-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("different keys are processed independently")
    void differentKeysProcessedIndependently() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Idem", "lastName", "Potent",
                "email", "idempotent2@test.local", "password", PASSWORD));

        mockMvc.perform(post("/api/v1/authentication/register")
                        .header("Idempotency-Key", "reg-key-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // A different key means a fresh execution, which now hits the duplicate email rule.
        mockMvc.perform(post("/api/v1/authentication/register")
                        .header("Idempotency-Key", "reg-key-B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }
}
