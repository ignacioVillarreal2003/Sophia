package com.sophia.documentworkspace.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.sophia.documentworkspace.support.AbstractIntegrationTest;
import com.sophia.documentworkspace.support.ApiClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Large-file upload flow: presigned part URLs against real MinIO, checksum
 * verification on completion, and the abort path.
 */
class MultipartUploadIT extends AbstractIntegrationTest {

    private ApiClient api;
    private String workspaceId;

    @BeforeAll
    void setUpWorkspace() {
        UUID userId = UUID.randomUUID();
        api = ApiClient.as(baseUrl(), userId, "mp-" + userId + "@x.com");
        workspaceId = api.post("/api/v1/workspaces", Map.of("name", "Multipart WS"))
                .data().path("id").asText();
    }

    private String base() {
        return "/api/v1/workspaces/" + workspaceId + "/uploads/multipart";
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    /** MinIO enforces a 5MB minimum for every part except the last. */
    private byte[] payload() {
        byte[] content = new byte[6 * 1024 * 1024];
        byte[] seed = "multipart-content-".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < content.length; i++) {
            content[i] = seed[i % seed.length];
        }
        return content;
    }

    @Test
    void initiateUploadPartsAndComplete() throws Exception {
        byte[] content = payload();
        String checksum = sha256(content);

        JsonNode session = api.post(base(), Map.of(
                        "filename", "big.bin",
                        "mimeType", "application/octet-stream",
                        "sizeBytes", content.length,
                        "checksumSha256", checksum))
                .data();
        String sessionId = session.path("sessionId").asText();
        JsonNode partUrls = session.path("partUrls");
        long partSize = session.path("partSizeBytes").asLong();
        assertThat(partUrls.size()).isGreaterThanOrEqualTo(1);

        // PUT each part directly to MinIO through its presigned URL.
        RestTemplate rest = new RestTemplate();
        Map<Integer, String> etags = new HashMap<>();
        for (JsonNode part : partUrls) {
            int partNumber = part.path("partNumber").asInt();
            long from = (long) (partNumber - 1) * partSize;
            long to = Math.min(from + partSize, content.length);
            if (from >= content.length) {
                break;
            }
            byte[] slice = java.util.Arrays.copyOfRange(content, (int) from, (int) to);
            ResponseEntity<Void> response = rest.exchange(URI.create(part.path("url").asText()),
                    HttpMethod.PUT, new HttpEntity<>(slice, new HttpHeaders()), Void.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            etags.put(partNumber, response.getHeaders().getFirst(HttpHeaders.ETAG));
        }

        JsonNode artifact = api.post(base() + "/" + sessionId + "/complete",
                Map.of("parts", etags)).data();
        assertThat(artifact.path("sizeBytes").asLong()).isEqualTo(content.length);
        assertThat(artifact.path("type").asText()).isEqualTo("BINARY_FILE");

        // The assembled object is downloadable and intact.
        String url = api.get("/api/v1/workspaces/" + workspaceId + "/files/"
                + artifact.path("id").asText() + "/download").data().path("url").asText();
        byte[] downloaded = new RestTemplate().getForObject(URI.create(url), byte[].class);
        assertThat(sha256(downloaded)).isEqualTo(checksum);
    }

    @Test
    void checksumMismatchDestroysTheUpload() throws Exception {
        byte[] content = payload();
        JsonNode session = api.post(base(), Map.of(
                        "filename", "corrupt.bin",
                        "sizeBytes", content.length,
                        "checksumSha256", "0".repeat(64)))
                .data();
        String sessionId = session.path("sessionId").asText();

        RestTemplate rest = new RestTemplate();
        Map<Integer, String> etags = new HashMap<>();
        JsonNode firstPart = session.path("partUrls").get(0);
        ResponseEntity<Void> put = rest.exchange(
                URI.create(firstPart.path("url").asText()),
                HttpMethod.PUT, new HttpEntity<>(content, new HttpHeaders()), Void.class);
        etags.put(1, put.getHeaders().getFirst(HttpHeaders.ETAG));

        ApiClient.Response completed = api.post(base() + "/" + sessionId + "/complete",
                Map.of("parts", etags));
        assertThat(completed.status()).isEqualTo(422);
        assertThat(completed.body().path("code").asText()).isEqualTo("CHECKSUM_MISMATCH");
    }

    @Test
    void abortInvalidatesTheSession() {
        JsonNode session = api.post(base(), Map.of(
                        "filename", "aborted.bin", "sizeBytes", 1024))
                .data();
        String sessionId = session.path("sessionId").asText();

        assertThat(api.delete(base() + "/" + sessionId).status()).isEqualTo(200);
        ApiClient.Response afterAbort = api.post(base() + "/" + sessionId + "/complete",
                Map.of("parts", Map.of(1, "etag")));
        assertThat(afterAbort.status()).isEqualTo(400);
    }
}
