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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Administrative endpoints with RBAC")
class AdminRbacIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("an ADMIN can manage users, roles and read audit logs")
    void adminFullCycle() throws Exception {
        String adminToken = loginAsAdmin("admin-cycle@test.local");

        // Create a user
        MvcResult created = mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Managed", "lastName", "User",
                                "email", "managed@test.local", "password", PASSWORD,
                                "roles", Set.of("USER"), "emailVerified", true))))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // List with pagination and search
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("search", "managed")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value("managed@test.local"));

        // Update roles
        mockMvc.perform(patch("/api/v1/users/{id}/roles", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roles", Set.of("USER", "ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles.length()").value(2));

        // Grant a direct permission
        mockMvc.perform(patch("/api/v1/users/{id}/permissions", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("permissions", Set.of("AUDIT_READ")))))
                .andExpect(status().isOk());

        // Deactivate
        mockMvc.perform(patch("/api/v1/users/{id}/status", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // Soft delete
        mockMvc.perform(delete("/api/v1/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("search", "managed"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(page.path("data").path("totalElements").asLong()).isZero();

        // Audit trail recorded the admin activity (persisted asynchronously)
        JsonNode auditContent = awaitAuditEntries(adminToken, "USER_CREATED");
        assertThat(auditContent.path(0).path("action").asText()).isEqualTo("USER_CREATED");
    }

    private JsonNode awaitAuditEntries(String adminToken, String action) throws Exception {
        JsonNode content = objectMapper.createArrayNode();
        for (int attempt = 0; attempt < 50; attempt++) {
            String body = mockMvc.perform(get("/api/v1/audit-logs")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .param("action", action))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            content = objectMapper.readTree(body).path("data").path("content");
            if (!content.isEmpty()) {
                return content;
            }
            Thread.sleep(100);
        }
        return content;
    }

    @Test
    @DisplayName("role CRUD works and system roles stay protected")
    void roleCrud() throws Exception {
        String adminToken = loginAsAdmin("admin-roles@test.local");

        MvcResult created = mockMvc.perform(post("/api/v1/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "SUPPORT_AGENT",
                                "description", "Handles support tickets",
                                "permissions", Set.of("USER_READ")))))
                .andExpect(status().isCreated())
                .andReturn();
        String roleId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(delete("/api/v1/roles/{id}", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // System role cannot be deleted
        String systemRoleId = roleRepository.findByName("USER").orElseThrow().getId().toString();
        mockMvc.perform(delete("/api/v1/roles/{id}", systemRoleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("permission CRUD works end to end")
    void permissionCrud() throws Exception {
        String adminToken = loginAsAdmin("admin-perms@test.local");

        MvcResult created = mockMvc.perform(post("/api/v1/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "REPORT_EXPORT",
                                "description", "Export reports",
                                "category", "REPORTS"))))
                .andExpect(status().isCreated())
                .andReturn();
        String permissionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(get("/api/v1/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/permissions/{id}", permissionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
