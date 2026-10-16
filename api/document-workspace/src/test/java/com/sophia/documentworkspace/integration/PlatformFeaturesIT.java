package com.sophia.documentworkspace.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.documentworkspace.application.service.MultipartUploadService;
import com.sophia.documentworkspace.application.service.TrashService;
import com.sophia.documentworkspace.domain.entity.OutboxEvent;
import com.sophia.documentworkspace.infrastructure.repository.OutboxEventRepository;
import com.sophia.documentworkspace.support.AbstractIntegrationTest;
import com.sophia.documentworkspace.support.ApiClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wide-surface integration pass: favorites/recents, tags, per-resource ACLs,
 * relabelling, locks, templates, comments-driven notifications, search, audit,
 * trash purge, workspace lifecycle and edge paths.
 */
class PlatformFeaturesIT extends AbstractIntegrationTest {

    private UUID ownerId;
    private UUID editorId;
    private ApiClient owner;
    private ApiClient editor;
    private String workspaceId;
    private String editorMemberId;

    @Autowired
    private TrashService trashService;
    @Autowired
    private MultipartUploadService multipartUploadService;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    void setUpWorkspace() {
        ownerId = UUID.randomUUID();
        editorId = UUID.randomUUID();
        String editorEmail = "editor-" + editorId + "@x.com";
        owner = ApiClient.as(baseUrl(), ownerId, "owner-" + ownerId + "@x.com");
        editor = ApiClient.as(baseUrl(), editorId, editorEmail);

        workspaceId = owner.post("/api/v1/workspaces", Map.of("name", "Features WS"))
                .data().path("id").asText();
        editorMemberId = addMember(owner, workspaceId, editorId, editorEmail, "EDITOR")
                .data().path("id").asText();
    }

    private String base() {
        return "/api/v1/workspaces/" + workspaceId;
    }

    private String newDocument(ApiClient who, String name) {
        return who.post(base() + "/artifacts",
                        Map.of("type", "MARKDOWN_DOCUMENT", "name", name))
                .data().path("id").asText();
    }

    @Test
    void workspaceUpdateSettingsAndListMine() {
        assertThat(owner.patch(base(), Map.of("description", "updated"))
                .data().path("description").asText()).isEqualTo("updated");
        assertThat(owner.patch(base() + "/settings", Map.of("timezone", "America/Montevideo"))
                .data().path("timezone").asText()).isEqualTo("America/Montevideo");

        JsonNode mine = owner.get("/api/v1/workspaces").data().path("content");
        assertThat(mine.findValuesAsText("id")).contains(workspaceId);
    }

    @Test
    void favoritesPinnedAndRecents() {
        String doc = newDocument(owner, "fav-doc");
        assertThat(owner.put(base() + "/favorites/" + doc, null).status()).isEqualTo(200);
        assertThat(owner.put(base() + "/favorites/" + doc + "/pinned?pinned=true", null)
                .data().path("pinned").asBoolean()).isTrue();
        assertThat(owner.get(base() + "/favorites?pinnedOnly=true").data().path("content")
                .findValuesAsText("artifactId")).contains(doc);

        owner.get(base() + "/artifacts/" + doc); // records a recent access
        assertThat(owner.get(base() + "/recent").data().findValuesAsText("id")).contains(doc);

        assertThat(owner.delete(base() + "/favorites/" + doc).status()).isEqualTo(200);
        assertThat(owner.get(base() + "/favorites").data().path("content")
                .findValuesAsText("artifactId")).doesNotContain(doc);
    }

    @Test
    void tagLifecycleAndAssignments() {
        String doc = newDocument(owner, "tagged-doc");
        String tagId = owner.post(base() + "/tags",
                        Map.of("name", "urgent-" + UUID.randomUUID(), "color", "#FF0000"))
                .data().path("id").asText();

        assertThat(owner.put(base() + "/artifacts/" + doc + "/tags/" + tagId, null).status())
                .isEqualTo(200);
        assertThat(owner.get(base() + "/artifacts/" + doc + "/tags").data()
                .findValuesAsText("id")).contains(tagId);
        assertThat(owner.patch(base() + "/tags/" + tagId, Map.of("color", "#00FF00"))
                .data().path("color").asText()).isEqualTo("#00FF00");
        assertThat(owner.get(base() + "/tags").data().path("content")
                .findValuesAsText("id")).contains(tagId);

        assertThat(owner.delete(base() + "/artifacts/" + doc + "/tags/" + tagId).status())
                .isEqualTo(200);
        assertThat(owner.delete(base() + "/tags/" + tagId).status()).isEqualTo(200);
    }

    @Test
    void aclGrantWidensAccessAndRevokeRemovesIt() {
        // A viewer-role member to exercise the discretionary layer.
        UUID viewerId = UUID.randomUUID();
        String viewerEmail = "viewer-" + viewerId + "@x.com";
        ApiClient viewer = ApiClient.as(baseUrl(), viewerId, viewerEmail);
        addMember(owner, workspaceId, viewerId, viewerEmail, "VIEWER");

        String doc = newDocument(owner, "acl-doc");
        assertThat(viewer.patch(base() + "/artifacts/" + doc, Map.of("name", "hacked")).status())
                .isEqualTo(403);

        String aclId = owner.post(base() + "/artifacts/" + doc + "/permissions", Map.of(
                        "subjectUserId", viewerId.toString(),
                        "modes", List.of("WRITE")))
                .data().path("id").asText();
        assertThat(viewer.patch(base() + "/artifacts/" + doc,
                Map.of("name", "renamed-by-acl")).status()).isEqualTo(200);
        assertThat(owner.get(base() + "/artifacts/" + doc + "/permissions").data()
                .findValuesAsText("id")).contains(aclId);

        assertThat(owner.delete(base() + "/artifacts/" + doc + "/permissions/" + aclId).status())
                .isEqualTo(200);
        assertThat(viewer.patch(base() + "/artifacts/" + doc, Map.of("name", "again")).status())
                .isEqualTo(403);
    }

    @Test
    void mentionCommentsEnqueueNotificationCommands() {
        String doc = newDocument(owner, "notify-doc");
        owner.post(base() + "/artifacts/" + doc + "/comments", Map.of(
                "body", "ping @editor", "mentions", List.of(editorId.toString())));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<OutboxEvent> commands = outboxEventRepository.findAll().stream()
                    .filter(event -> "notification-command".equals(event.getEventType()))
                    .filter(event -> editorId.toString().equals(event.getAggregateId()))
                    .toList();
            assertThat(commands).isNotEmpty();
            OutboxEvent command = commands.getLast();
            assertThat(command.getTopic()).isEqualTo("sophia.notifications.commands");
            JsonNode payload = objectMapper.readTree(command.getPayload());
            assertThat(payload.path("category").asText()).isEqualTo("DOCUMENT_WORKSPACE_MENTION");
            assertThat(payload.path("recipient").path("userId").asText())
                    .isEqualTo(editorId.toString());
            assertThat(payload.path("inApp").path("title").asText())
                    .isEqualTo("You were mentioned in a comment");
            assertThat(payload.path("email").path("template").asText())
                    .isEqualTo("generic.notification");
        });
    }

    @Test
    void searchFindsContent() {
        String marker = "zebrafish" + UUID.randomUUID().toString().substring(0, 8);
        String doc = owner.post(base() + "/artifacts", Map.of(
                        "type", "MARKDOWN_DOCUMENT", "name", "searchable " + marker))
                .data().path("id").asText();

        // Indexing is async: poll until the owner sees the hit.
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JsonNode hits = owner.get(base() + "/search?q=" + marker).data().path("hits");
            assertThat(hits.findValuesAsText("artifactId")).contains(doc);
        });

    }

    @Test
    void auditTrailRecordsDomainEvents() {
        Instant before = Instant.now().minusSeconds(5);
        String doc = newDocument(owner, "audited-doc");
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode entries = owner.get(base() + "/audit?action=artifact.created")
                    .data().path("content");
            assertThat(entries.findValuesAsText("resourceId")).contains(doc);
        });
        Instant after = Instant.now().plusSeconds(5);

        // Each independent filter narrows the same row down without excluding it.
        assertThat(owner.get(base() + "/audit?userId=" + ownerId)
                .data().path("content").findValuesAsText("resourceId")).contains(doc);
        assertThat(owner.get(base() + "/audit?resourceType=artifact")
                .data().path("content").findValuesAsText("resourceId")).contains(doc);
        assertThat(owner.get(base() + "/audit?resourceId=" + doc)
                .data().path("content").findValuesAsText("resourceId")).contains(doc);
        assertThat(owner.get(base() + "/audit?result=SUCCESS")
                .data().path("content").findValuesAsText("resourceId")).contains(doc);
        assertThat(owner.get(base() + "/audit?from=" + before + "&to=" + after)
                .data().path("content").findValuesAsText("resourceId")).contains(doc);

        // A filter that can't match this row excludes it.
        assertThat(owner.get(base() + "/audit?resourceType=workspace")
                .data().path("content").findValuesAsText("resourceId")).doesNotContain(doc);
        assertThat(owner.get(base() + "/audit?to=" + before)
                .data().path("content").findValuesAsText("resourceId")).doesNotContain(doc);

        // The editor lacks VIEW_AUDIT.
        assertThat(editor.get(base() + "/audit").status()).isEqualTo(403);
    }

    @Test
    void trashPurgeNowAndEmptyTrash() {
        String purged = newDocument(owner, "purge-me");
        owner.delete(base() + "/artifacts/" + purged);
        assertThat(owner.delete(base() + "/trash/" + purged).status()).isEqualTo(200);
        assertThat(owner.get(base() + "/artifacts/" + purged).status()).isEqualTo(404);

        String emptied = newDocument(owner, "empty-me");
        owner.delete(base() + "/artifacts/" + emptied);
        assertThat(owner.delete(base() + "/trash").status()).isEqualTo(200);
        assertThat(owner.get(base() + "/trash").data().path("content")
                .findValuesAsText("id")).doesNotContain(emptied);

        // The scheduled jobs run without error when invoked directly.
        trashService.purgeExpired();
        multipartUploadService.sweepExpiredSessions();
    }

    @Test
    void platformAdminSuspendsAndReactivatesWorkspace() {
        UUID adminId = UUID.randomUUID();
        ApiClient platformAdmin = ApiClient.as(baseUrl(), adminId, "admin@x.com",
                List.of("ADMIN"), List.of("WORKSPACE_MANAGE"));
        ApiClient tempOwner = ApiClient.as(baseUrl(), UUID.randomUUID(), "tmp@x.com");
        String tempWs = tempOwner.post("/api/v1/workspaces", Map.of("name", "Suspend WS"))
                .data().path("id").asText();

        assertThat(platformAdmin.post("/api/v1/workspaces/" + tempWs + "/suspend", null)
                .status()).isEqualTo(200);
        assertThat(tempOwner.get("/api/v1/workspaces/" + tempWs).status()).isEqualTo(403);
        assertThat(platformAdmin.post("/api/v1/workspaces/" + tempWs + "/reactivate", null)
                .status()).isEqualTo(200);
        assertThat(tempOwner.get("/api/v1/workspaces/" + tempWs).status()).isEqualTo(200);

        // Soft delete + restore by the owner.
        assertThat(tempOwner.delete("/api/v1/workspaces/" + tempWs).status()).isEqualTo(200);
        assertThat(tempOwner.get("/api/v1/workspaces/" + tempWs).status()).isEqualTo(404);
        assertThat(tempOwner.post("/api/v1/workspaces/" + tempWs + "/restore", null).status())
                .isEqualTo(200);
        assertThat(tempOwner.get("/api/v1/workspaces/" + tempWs).status()).isEqualTo(200);
    }
}
