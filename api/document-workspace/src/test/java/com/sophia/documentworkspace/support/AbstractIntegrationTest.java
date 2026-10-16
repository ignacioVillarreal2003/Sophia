package com.sophia.documentworkspace.support;

import com.sophia.documentworkspace.infrastructure.client.UserDirectoryClient;
import org.junit.jupiter.api.TestInstance;
import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * Base of every integration test: boots the full application against real
 * infrastructure (PostgreSQL, Redis, Kafka, OpenSearch, MinIO) provided by
 * Testcontainers. Containers are singletons shared by every IT class of the
 * JVM run — started once, reused, and reaped by Ryuk at the end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest {

    /** Shared HS256 secret between the test JWT factory and the application. */
    public static final String JWT_SECRET =
            "integration-test-secret-0123456789abcdef0123456789abcdef";

    private static final PostgreSQLContainer<?> POSTGRES;
    private static final GenericContainer<?> REDIS;
    private static final KafkaContainer KAFKA;
    private static final OpensearchContainer<?> OPENSEARCH;
    private static final MinIOContainer MINIO;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("sophia_documents")
                .withUsername("sophia")
                .withPassword("sophia");
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        // The confluentinc image via the classic KafkaContainer: the apache/kafka
        // image's KafkaDockerWrapper fails its storage-format step on some Docker
        // Desktop installations regardless of configuration.
        @SuppressWarnings("deprecation")
        KafkaContainer kafka = new KafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.4.10"));
        KAFKA = kafka;
        OPENSEARCH = new OpensearchContainer<>(
                DockerImageName.parse("opensearchproject/opensearch:2.19.1"));
        MINIO = new MinIOContainer(DockerImageName.parse(
                "minio/minio:RELEASE.2025-09-07T16-13-09Z"));

        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
        OPENSEARCH.start();
        MINIO.start();
    }

    @LocalServerPort
    protected int port;

    @org.springframework.beans.factory.annotation.Autowired
    private software.amazon.awssdk.services.s3.S3Client s3Client;

    @org.junit.jupiter.api.BeforeAll
    void ensureBucket() {
        try {
            s3Client.createBucket(b -> b.bucket("sophia-it"));
        } catch (software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException ignored) {
            // already created by a previous test class
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        registry.add("sophia.search.host", OPENSEARCH::getHost);
        registry.add("sophia.search.port", () -> OPENSEARCH.getMappedPort(9200));
        registry.add("sophia.search.scheme", () -> "http");

        registry.add("sophia.storage.endpoint", MINIO::getS3URL);
        registry.add("sophia.storage.access-key", MINIO::getUserName);
        registry.add("sophia.storage.secret-key", MINIO::getPassword);
        registry.add("sophia.storage.bucket", () -> "sophia-it");
        registry.add("sophia.storage.path-style-access", () -> "true");

        // Force the HS256 path: the dev profile defaults to the JSON Web Key
        // Set endpoint of a local authentication service, which does not
        // exist in the test stack.
        registry.add("sophia.security.jwt.json-web-key-set-uri", () -> "");
        registry.add("sophia.security.jwt.secret", () -> JWT_SECRET);
        registry.add("sophia.security.jwt.issuer", () -> "sophia-authentication");

        // Fast outbox relay so event-driven assertions settle quickly.
        registry.add("sophia.events.relay-interval", () -> "500ms");
    }

    /** The authentication service is not part of the test stack: users are resolved from this stub. */
    @MockitoBean
    protected UserDirectoryClient userDirectory;

    /** Adds a (stubbed) platform user to a workspace through the real endpoint, acting as {@code admin}. */
    protected ApiClient.Response addMember(ApiClient admin, String workspaceId, UUID userId, String email,
                                           String role) {
        when(userDirectory.findByEmail(email)).thenReturn(
                Optional.of(new UserDirectoryClient.UserSummary(userId, email, email, null)));
        return admin.post("/api/v1/workspaces/" + workspaceId + "/members",
                Map.of("email", email, "role", role));
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }
}
