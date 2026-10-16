package com.sophia.documentworkspace.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.sophia.documentworkspace.support.AbstractIntegrationTest;
import com.sophia.documentworkspace.support.ApiClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hierarchy end to end: move-cycle protection and the trash lifecycle.
 */
class ArtifactMacIT extends AbstractIntegrationTest {

    private ApiClient owner;
    private ApiClient viewer;
    private String workspaceId;

    @BeforeAll
    void setUpWorkspace() {
        UUID ownerId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        String viewerEmail = "viewer-" + viewerId + "@x.com";
        owner = ApiClient.as(baseUrl(), ownerId, "owner-" + ownerId + "@x.com");
        viewer = ApiClient.as(baseUrl(), viewerId, viewerEmail);

        workspaceId = owner.post("/api/v1/workspaces", Map.of("name", "MAC WS"))
                .data().path("id").asText();
        addMember(owner, workspaceId, viewerId, viewerEmail, "EDITOR");
    }

    private String artifacts() {
        return "/api/v1/workspaces/" + workspaceId + "/artifacts";
    }

    @Test
    void movingIntoOwnSubtreeIsRejected() {
        String parent = owner.post(artifacts(), Map.of("type", "FOLDER", "name", "p"))
                .data().path("id").asText();
        String child = owner.post(artifacts(), Map.of(
                        "type", "FOLDER", "name", "c", "parentId", parent))
                .data().path("id").asText();

        ApiClient.Response cycle = owner.put(artifacts() + "/" + parent + "/parent",
                Map.of("newParentId", child));
        assertThat(cycle.status()).isEqualTo(400);
        assertThat(cycle.body().path("code").asText()).isEqualTo("INVALID_HIERARCHY");
    }

    @Test
    void trashRestoreAndBreadcrumb() {
        String folder = owner.post(artifacts(), Map.of("type", "FOLDER", "name", "docs"))
                .data().path("id").asText();
        String doc = owner.post(artifacts(), Map.of(
                        "type", "MARKDOWN_DOCUMENT", "name", "inside", "parentId", folder))
                .data().path("id").asText();

        // Breadcrumb of the child lists the folder.
        JsonNode breadcrumb = owner.get(artifacts() + "/" + doc + "/breadcrumb").data();
        assertThat(breadcrumb.findValuesAsText("id")).containsExactly(folder);

        // Trashing the folder takes the whole subtree with it.
        assertThat(owner.delete(artifacts() + "/" + folder).status()).isEqualTo(200);
        assertThat(owner.get(artifacts() + "/" + doc).status()).isEqualTo(404);

        // Trash lists only the subtree root.
        JsonNode trash = owner.get("/api/v1/workspaces/" + workspaceId + "/trash")
                .data().path("content");
        assertThat(trash.findValuesAsText("id")).contains(folder).doesNotContain(doc);

        // Restore brings the subtree back.
        assertThat(owner.post("/api/v1/workspaces/" + workspaceId + "/trash/" + folder
                + "/restore", null).status()).isEqualTo(200);
        assertThat(owner.get(artifacts() + "/" + doc).status()).isEqualTo(200);
    }

    @Test
    void errorShapesAreUniform() {
        // Bean validation failure carries field errors.
        ApiClient.Response invalid = owner.post(artifacts(), Map.of("type", "FOLDER", "name", ""));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(invalid.body().path("fieldErrors")).isNotEmpty();
        assertThat(invalid.body().path("requestId").asText()).isNotBlank();

        // Unknown endpoint and malformed UUID parameter.
        assertThat(owner.get("/api/v1/nope").status()).isEqualTo(404);
        assertThat(owner.get(artifacts() + "/not-a-uuid").status()).isEqualTo(400);
    }


}
