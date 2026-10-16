package com.sophia.notification.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.notification.support.AbstractIntegrationTest;
import com.sophia.notification.support.TestJwtFactory;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationPlatformIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID userId = UUID.randomUUID();

    @Test
    @Order(1)
    void userLifecycleEventsBuildTheContactProjectionAndCommandsFanOut() {
        kafkaTemplate.send("sophia.authentication.user-events", userId.toString(), """
                {"eventType":"USER_CREATED","userId":"%s","email":"ada@test.local",
                 "firstName":"Ada","lastName":"Lovelace",
                 "occurredAt":"%s"}
                """.formatted(userId, Instant.now()));

        String commandJson = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"DOCUMENT_WORKSPACE_MENTION",
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"MENTION","title":"You were mentioned","body":"In a comment",
                          "data":{"workspaceId":"%s"}},
                 "email":{"template":"generic.notification",
                          "variables":{"title":"You were mentioned","body":"In a comment"}}}
                """.formatted(UUID.randomUUID(), Instant.now(), userId, UUID.randomUUID());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            kafkaTemplate.send("sophia.notifications.commands", userId.toString(), commandJson);
            JsonNode unread = get("/api/v1/notifications/unread-count").path("data");
            assertThat(unread.path("unread").asLong()).isGreaterThan(0);
        });

        JsonNode inbox = get("/api/v1/notifications?unreadOnly=true").path("data");
        JsonNode first = inbox.path("content").get(0);
        assertThat(first.path("title").asText()).isEqualTo("You were mentioned");
        assertThat(first.path("category").asText()).isEqualTo("DOCUMENT_WORKSPACE_MENTION");
        assertThat(first.path("data").path("workspaceId").asText()).isNotBlank();

        String notificationId = first.path("id").asText();
        assertThat(post("/api/v1/notifications/" + notificationId + "/read")
                .path("data").path("read").asBoolean()).isTrue();
        post("/api/v1/notifications/read-all");
        assertThat(get("/api/v1/notifications/unread-count")
                .path("data").path("unread").asLong()).isZero();
    }

    @Test
    @Order(2)
    void duplicateCommandsAreProcessedOnce() {
        String messageId = UUID.randomUUID().toString();
        String commandJson = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"SYSTEM",
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"SYSTEM","title":"Duplicated once"}}
                """.formatted(messageId, Instant.now(), userId);

        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), commandJson);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), commandJson);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<String> titles = get("/api/v1/notifications?size=50").path("data").path("content")
                    .findValuesAsText("title");
            assertThat(titles).containsOnlyOnce("Duplicated once");
        });
    }

    @Test
    @Order(3)
    void preferencesSilenceOptionalCategories() {
        JsonNode updated = put("/api/v1/notification-preferences", """
                {"categories":{"DOCUMENT_WORKSPACE_COMMENT":{"IN_APP":false}}}
                """).path("data");
        assertThat(updated.path("categories").path("DOCUMENT_WORKSPACE_COMMENT").path("IN_APP").asBoolean())
                .isFalse();
        assertThat(updated.path("categories").has("AUTHENTICATION_SECURITY")).isFalse();

        String commandJson = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"DOCUMENT_WORKSPACE_COMMENT",
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"COMMENT","title":"Silenced comment"}}
                """.formatted(UUID.randomUUID(), Instant.now(), userId);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), commandJson);

        String controlJson = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"SYSTEM",
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"MEMBERSHIP","title":"Control notification"}}
                """.formatted(UUID.randomUUID(), Instant.now(), userId);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), controlJson);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<String> titles = get("/api/v1/notifications?size=50").path("data").path("content")
                    .findValuesAsText("title");
            assertThat(titles).contains("Control notification");
            assertThat(titles).doesNotContain("Silenced comment");
        });
    }

    @Test
    @Order(4)
    void anonymousRequestsAreRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/api/v1/notifications"), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @Order(5)
    void onlyPrivilegedCallersCanManageNotificationCategoriesAndNewOnesWorkImmediately() {
        ResponseEntity<String> denied = restTemplate.exchange(
                url("/api/v1/notification-categories"), HttpMethod.GET,
                new HttpEntity<>(null, bearerHeaders(token())), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);

        JsonNode created = managementExchange("/api/v1/notification-categories", HttpMethod.POST, """
                {"code":"AUDIT","name":"Audit","description":"Audit trail notices","mandatory":false}
                """).path("data");
        assertThat(created.path("code").asText()).isEqualTo("AUDIT");

        String commandJson = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"AUDIT",
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"AUDIT","title":"Audit event"}}
                """.formatted(UUID.randomUUID(), Instant.now(), userId);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), commandJson);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<String> titles = get("/api/v1/notifications?size=50").path("data").path("content")
                    .findValuesAsText("title");
            assertThat(titles).contains("Audit event");
        });
    }

    @Test
    @Order(6)
    void onlyPrivilegedCallersCanViewAnotherUsersInbox() {
        ResponseEntity<String> denied = restTemplate.exchange(
                url("/api/v1/users/" + userId + "/notifications"), HttpMethod.GET,
                new HttpEntity<>(null, bearerHeaders(token())), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);

        JsonNode inbox = exchange("/api/v1/users/" + userId + "/notifications",
                HttpMethod.GET, null, managementToken()).path("data");
        assertThat(inbox.path("content").size()).isGreaterThan(0);
    }

    @Test
    @Order(7)
    void anEmailCommandIsRenderedAndActuallyDelivered() {
        String commandJson = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"AUTHENTICATION_SECURITY",
                 "recipient":{"userId":"%s","email":"ada@test.local","locale":"es"},
                 "email":{"template":"security.password-reset",
                          "variables":{"name":"Ada","token":"raw-token","ttlMinutes":"30"}}}
                """.formatted(UUID.randomUUID(), Instant.now(), userId);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), commandJson);

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(mailhog("/api/v2/search?kind=to&query=ada@test.local").path("total").asInt())
                        .as("the outbox dispatcher handed the email to the mail server")
                        .isPositive());
    }

    @Test
    @Order(8)
    void aCommandTheServiceCannotAcceptEndsUpOnTheDeadLetterTopic() {
        String rejected = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"NOT_A_CATEGORY",
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"SYSTEM","title":"Never delivered"}}
                """.formatted(UUID.randomUUID(), Instant.now(), userId);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), rejected);

        await().atMost(Duration.ofSeconds(90)).untilAsserted(() ->
                assertThat(deadLetterCount("sophia.notifications.commands.dlt")).isPositive());
    }

    @Test
    @Order(9)
    void scheduledNotificationsStayOutOfTheInboxUntilTheirTime() {
        String scheduled = """
                {"messageId":"%s","occurredAt":"%s","scheduledAt":"%s",
                 "category":"SYSTEM",
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"SYSTEM","title":"From the future"}}
                """.formatted(UUID.randomUUID(), Instant.now(), Instant.now().plus(Duration.ofDays(1)), userId);
        String immediate = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"SYSTEM",
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"SYSTEM","title":"From right now"}}
                """.formatted(UUID.randomUUID(), Instant.now(), userId);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), scheduled);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), immediate);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<String> titles = titles("/api/v1/notifications?size=100");
            assertThat(titles).contains("From right now");
            assertThat(titles).doesNotContain("From the future");
        });
    }

    @Test
    @Order(10)
    void expiredNotificationsDisappearFromTheInboxImmediately() {
        String expired = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"SYSTEM",
                 "attributes":{"expiresAt":"%s"},
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"SYSTEM","title":"Already stale"}}
                """.formatted(UUID.randomUUID(), Instant.now(), Instant.now().minusSeconds(5), userId);
        String control = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"SYSTEM",
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"SYSTEM","title":"Still fresh"}}
                """.formatted(UUID.randomUUID(), Instant.now(), userId);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), expired);
        kafkaTemplate.send("sophia.notifications.commands", userId.toString(), control);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<String> titles = titles("/api/v1/notifications?size=100");
            assertThat(titles).contains("Still fresh");
            assertThat(titles).doesNotContain("Already stale");
        });
    }

    @Test
    @Order(11)
    void notificationsCanBeFilteredSortedAndGrouped() {
        String grouped = """
                {"messageId":"%s","occurredAt":"%s",
                 "category":"DOCUMENT_WORKSPACE_SHARING",
                 "attributes":{"severity":"WARNING","groupKey":"thread-42"},
                 "recipient":{"userId":"%s"},
                 "inApp":{"type":"COMMENT","title":"Grouped comment %d"}}
                """;
        for (int i = 0; i < 3; i++) {
            kafkaTemplate.send("sophia.notifications.commands", userId.toString(),
                    grouped.formatted(UUID.randomUUID(), Instant.now(), userId, i));
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode groups = get("/api/v1/notifications/groups").path("data");
            JsonNode thread = null;
            for (JsonNode group : groups) {
                if ("thread-42".equals(group.path("groupKey").asText())) {
                    thread = group;
                }
            }
            assertThat(thread).isNotNull();
            assertThat(thread.path("total").asLong()).isEqualTo(3);
        });

        assertThat(titles("/api/v1/notifications?severity=WARNING&size=100"))
                .allMatch(title -> title.startsWith("Grouped comment"));
        assertThat(titles("/api/v1/notifications?search=grouped comment&size=100")).hasSize(3);
        assertThat(titles("/api/v1/notifications?category=DOCUMENT_WORKSPACE_SHARING&size=100")).hasSize(3);

        ResponseEntity<String> badSort = restTemplate.exchange(
                url("/api/v1/notifications?sort=recipientId,asc"), HttpMethod.GET,
                new HttpEntity<>(null, bearerHeaders(token())), String.class);
        assertThat(badSort.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @Order(12)
    void deletingAUserRemovesEverythingWeHoldAboutThem() {
        kafkaTemplate.send("sophia.authentication.user-events", userId.toString(), """
                {"eventType":"USER_DELETED","userId":"%s","occurredAt":"%s"}
                """.formatted(userId, Instant.now()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/api/v1/notifications?size=100").path("data").path("content").size()).isZero());
    }

    private long deadLetterCount(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-probe-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            return consumer.poll(Duration.ofSeconds(5)).count();
        }
    }

    private JsonNode mailhog(String path) {
        String base = "http://" + MAILHOG.getHost() + ":" + MAILHOG.getMappedPort(8025);
        try {
            return objectMapper.readTree(restTemplate.getForObject(base + path, String.class));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the mail server", e);
        }
    }

    private List<String> titles(String path) {
        return get(path).path("data").path("content").findValuesAsText("title");
    }

    private JsonNode get(String path) {
        return exchange(path, HttpMethod.GET, null, token());
    }

    private JsonNode post(String path) {
        return exchange(path, HttpMethod.POST, null, token());
    }

    private JsonNode put(String path, String body) {
        return exchange(path, HttpMethod.PUT, body, token());
    }

    private JsonNode managementExchange(String path, HttpMethod method, String body) {
        return exchange(path, method, body, managementToken());
    }

    private JsonNode exchange(String path, HttpMethod method, String body, String bearerToken) {
        HttpHeaders headers = bearerHeaders(bearerToken);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                url(path), method, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .withFailMessage("Unexpected status %s for %s %s: %s",
                        response.getStatusCode(), method, path, response.getBody())
                .isTrue();
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable response for " + path, e);
        }
    }

    private HttpHeaders bearerHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        return headers;
    }

    private String token() {
        return token(List.of("USER"),
                List.of("NOTIFICATION_READ", "NOTIFICATION_UPDATE", "NOTIFICATION_DELETE",
                        "NOTIFICATION_PREFERENCE_READ", "NOTIFICATION_PREFERENCE_UPDATE"));
    }

    private String managementToken() {
        return token(List.of("SUPPORT"),
                List.of("NOTIFICATION_CATEGORY_READ", "NOTIFICATION_CATEGORY_CREATE",
                        "NOTIFICATION_CATEGORY_UPDATE", "NOTIFICATION_CATEGORY_DELETE",
                        "NOTIFICATION_PREFERENCE_READ_UNRESTRICTED", "NOTIFICATION_PREFERENCE_UPDATE_UNRESTRICTED",
                        "NOTIFICATION_READ_UNRESTRICTED"));
    }

    private String token(List<String> roles, List<String> permissions) {
        return TestJwtFactory.token(userId, "ada@test.local", roles, permissions);
    }
}
