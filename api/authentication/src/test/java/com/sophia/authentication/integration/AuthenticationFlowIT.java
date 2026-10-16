package com.sophia.authentication.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.sophia.authentication.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("End-to-end authentication flows")
class AuthenticationFlowIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("register → verify → login → me → rotate → reuse detection")
    void fullLifecycle() throws Exception {
        String email = "lifecycle@test.local";
        JsonNode auth = registerVerifyAndLogin(email);

        String accessToken = auth.path("accessToken").asText();
        String refreshToken = auth.path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        // Profile with the issued access token
        mockMvc.perform(get("/api/v1/authentication/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));

        // Rotate the refresh token
        MvcResult rotated = mockMvc.perform(post("/api/v1/authentication/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        String newRefreshToken = objectMapper.readTree(rotated.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();
        assertThat(newRefreshToken).isNotBlank().isNotEqualTo(refreshToken);

        // Reusing the rotated token is treated as theft…
        mockMvc.perform(post("/api/v1/authentication/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());

        // …and revokes the whole session family, including the new token.
        mockMvc.perform(post("/api/v1/authentication/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", newRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login is rejected until the email is verified")
    void loginRequiresVerifiedEmail() throws Exception {
        String email = "unverified@test.local";
        register(email);

        mockMvc.perform(post("/api/v1/authentication/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the account locks after repeated failures")
    void bruteForceLocksAccount() throws Exception {
        String email = "bruteforce@test.local";
        register(email);
        verifyEmail(capturedVerificationToken());

        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/v1/authentication/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Wrong!Pass1"))))
                    .andExpect(status().isUnauthorized());
        }
        // 5th failure crosses the threshold and locks the account
        mockMvc.perform(post("/api/v1/authentication/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Wrong!Pass1"))))
                .andExpect(status().isLocked());

        // Even the correct password is rejected while locked
        mockMvc.perform(post("/api/v1/authentication/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("logout blacklists the access token and kills the refresh session")
    void logoutInvalidatesTokens() throws Exception {
        JsonNode auth = registerVerifyAndLogin("logout@test.local");
        String accessToken = auth.path("accessToken").asText();
        String refreshToken = auth.path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/authentication/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/authentication/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/authentication/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("duplicate registration returns 409 with the standard error shape")
    void duplicateRegistration() throws Exception {
        String email = "duplicate@test.local";
        register(email);

        mockMvc.perform(post("/api/v1/authentication/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Jane", "lastName", "Doe",
                                "email", email, "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("weak passwords are rejected with field-level validation errors")
    void weakPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/v1/authentication/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Jane", "lastName", "Doe",
                                "email", "weak@test.local", "password", "weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("password reset flow issues a token, resets and revokes sessions")
    void passwordResetFlow() throws Exception {
        String email = "reset@test.local";
        JsonNode auth = registerVerifyAndLogin(email);
        String refreshToken = auth.path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/authentication/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailService)
                .sendPasswordResetEmail(org.mockito.ArgumentMatchers.argThat(
                        user -> user != null && email.equals(user.getEmail())), captor.capture());

        String newPassword = "N3w!Password";
        mockMvc.perform(post("/api/v1/authentication/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", captor.getValue(), "newPassword", newPassword))))
                .andExpect(status().isOk());

        // Old refresh token was revoked by the reset
        mockMvc.perform(post("/api/v1/authentication/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());

        // The new password works
        login(email, newPassword);
    }
}
