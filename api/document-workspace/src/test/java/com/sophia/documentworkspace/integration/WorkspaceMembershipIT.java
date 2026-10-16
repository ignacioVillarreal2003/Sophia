package com.sophia.documentworkspace.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.sophia.documentworkspace.support.AbstractIntegrationTest;
import com.sophia.documentworkspace.support.ApiClient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end workspace + membership flow over the real stack.
 */
class WorkspaceMembershipIT extends AbstractIntegrationTest {

    @Test
    void fullMembershipLifecycle() {
        UUID ownerId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        String inviteeEmail = "invitee-" + inviteeId + "@x.com";
        ApiClient owner = ApiClient.as(baseUrl(), ownerId, "owner-" + ownerId + "@x.com");
        ApiClient invitee = ApiClient.as(baseUrl(), inviteeId, inviteeEmail);

        // Create workspace: the creator becomes its owner (stored as an ADMIN member).
        ApiClient.Response created = owner.post("/api/v1/workspaces",
                Map.of("name", "IT Workspace"));
        assertThat(created.status()).isEqualTo(201);
        String workspaceId = created.data().path("id").asText();
        assertThat(created.data().path("myRole").asText()).isEqualTo("ADMIN");
        assertThat(created.data().path("ownerId").asText()).isEqualTo(ownerId.toString());

        // Non-members cannot see it.
        assertThat(invitee.get("/api/v1/workspaces/" + workspaceId).status()).isEqualTo(403);

        // The owner (an ADMIN) adds a member directly.
        ApiClient.Response added = addMember(owner, workspaceId, inviteeId, inviteeEmail, "EDITOR");
        assertThat(added.status()).isEqualTo(201);
        assertThat(added.data().path("role").asText()).isEqualTo("EDITOR");
        assertThat(added.data().path("invitedBy").asText()).isEqualTo(ownerId.toString());
        String memberId = added.data().path("id").asText();

        // Adding the same person twice conflicts.
        assertThat(addMember(owner, workspaceId, inviteeId, inviteeEmail, "EDITOR").status())
                .isEqualTo(409);

        // The member sees the workspace now, but an EDITOR cannot manage members.
        assertThat(invitee.get("/api/v1/workspaces/" + workspaceId).status()).isEqualTo(200);
        assertThat(invitee.put("/api/v1/workspaces/" + workspaceId + "/members/" + memberId
                + "/role", Map.of("role", "VIEWER")).status()).isEqualTo(403);
        assertThat(addMember(invitee, workspaceId, UUID.randomUUID(), "other@x.com", "VIEWER").status())
                .isEqualTo(403);

        // The owner promotes the member to ADMIN.
        ApiClient.Response roleChanged = owner.put(
                "/api/v1/workspaces/" + workspaceId + "/members/" + memberId + "/role",
                Map.of("role", "ADMIN"));
        assertThat(roleChanged.status()).isEqualTo(200);
        assertThat(roleChanged.data().path("role").asText()).isEqualTo("ADMIN");
        assertThat(roleChanged.data().path("updatedBy").asText()).isEqualTo(ownerId.toString());

        // An ADMIN may toggle EDITOR <-> VIEWER but cannot touch other admins or the owner.
        UUID viewerId = UUID.randomUUID();
        String viewerMemberId = addMember(owner, workspaceId, viewerId, "v-" + viewerId + "@x.com", "VIEWER")
                .data().path("id").asText();
        assertThat(invitee.put("/api/v1/workspaces/" + workspaceId + "/members/" + viewerMemberId
                + "/role", Map.of("role", "EDITOR")).status()).isEqualTo(200);
        assertThat(invitee.put("/api/v1/workspaces/" + workspaceId + "/members/" + viewerMemberId
                + "/role", Map.of("role", "ADMIN")).status()).isEqualTo(403);

        // Only the owner can suspend: an ADMIN gets 403, the owner succeeds.
        assertThat(invitee.post("/api/v1/workspaces/" + workspaceId + "/members/" + viewerMemberId
                + "/suspend", null).status()).isEqualTo(403);
        assertThat(owner.post("/api/v1/workspaces/" + workspaceId + "/members/" + memberId
                + "/suspend", null).status()).isEqualTo(200);
        assertThat(invitee.get("/api/v1/workspaces/" + workspaceId).status()).isEqualTo(403);
        assertThat(owner.post("/api/v1/workspaces/" + workspaceId + "/members/" + memberId
                + "/reactivate", null).status()).isEqualTo(200);
        assertThat(invitee.get("/api/v1/workspaces/" + workspaceId).status()).isEqualTo(200);

        // Leaving works for non-owners; owners must transfer first.
        assertThat(owner.delete("/api/v1/workspaces/" + workspaceId + "/members/me").status())
                .isEqualTo(409);
        assertThat(invitee.delete("/api/v1/workspaces/" + workspaceId + "/members/me").status())
                .isEqualTo(200);
        assertThat(invitee.get("/api/v1/workspaces/" + workspaceId).status()).isEqualTo(403);
    }

    @Test
    void ownershipTransferMovesOwnerId() {
        UUID ownerId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();
        String successorEmail = "successor-" + successorId + "@x.com";
        ApiClient owner = ApiClient.as(baseUrl(), ownerId, "owner2-" + ownerId + "@x.com");
        ApiClient successor = ApiClient.as(baseUrl(), successorId, successorEmail);

        String workspaceId = owner.post("/api/v1/workspaces", Map.of("name", "Transfer WS"))
                .data().path("id").asText();
        addMember(owner, workspaceId, successorId, successorEmail, "ADMIN");

        assertThat(owner.put("/api/v1/workspaces/" + workspaceId + "/owner",
                Map.of("newOwnerUserId", successorId.toString())).status()).isEqualTo(200);

        JsonNode workspace = successor.get("/api/v1/workspaces/" + workspaceId).data();
        assertThat(workspace.path("ownerId").asText()).isEqualTo(successorId.toString());
        assertThat(workspace.path("myRole").asText()).isEqualTo("ADMIN");
        assertThat(owner.get("/api/v1/workspaces/" + workspaceId).data()
                .path("myRole").asText()).isEqualTo("ADMIN");
    }

    @Test
    void listMineFiltersBySearchRoleAndStatus() {
        UUID ownerId = UUID.randomUUID();
        ApiClient owner = ApiClient.as(baseUrl(), ownerId, "owner3-" + ownerId + "@x.com");
        String marker = "Filterable-" + UUID.randomUUID();
        String workspaceId = owner.post("/api/v1/workspaces", Map.of("name", marker))
                .data().path("id").asText();

        assertThat(owner.get("/api/v1/workspaces?search=" + marker)
                .data().path("content").findValuesAsText("id")).contains(workspaceId);
        assertThat(owner.get("/api/v1/workspaces?role=ADMIN")
                .data().path("content").findValuesAsText("id")).contains(workspaceId);
        assertThat(owner.get("/api/v1/workspaces?status=ACTIVE")
                .data().path("content").findValuesAsText("id")).contains(workspaceId);

        assertThat(owner.get("/api/v1/workspaces?search=" + UUID.randomUUID())
                .data().path("content").findValuesAsText("id")).doesNotContain(workspaceId);
        assertThat(owner.get("/api/v1/workspaces?role=VIEWER")
                .data().path("content").findValuesAsText("id")).doesNotContain(workspaceId);
        assertThat(owner.get("/api/v1/workspaces?status=READ_ONLY")
                .data().path("content").findValuesAsText("id")).doesNotContain(workspaceId);
    }

    @Test
    void anonymousRequestsGet401() {
        ApiClient anonymous = ApiClient.anonymous(baseUrl());
        ApiClient.Response response = anonymous.get("/api/v1/workspaces");
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body().path("code").asText()).isEqualTo("UNAUTHORIZED");
    }
}
