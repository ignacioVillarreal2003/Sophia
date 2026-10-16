package com.sophia.documentworkspace.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Minimal authenticated JSON client for integration tests. Every response is
 * returned as (status, parsed body) so tests can assert error paths without
 * exception handling noise.
 */
public final class ApiClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;

    public record Response(int status, JsonNode body) {

        public JsonNode data() {
            return body.path("data");
        }
    }

    private ApiClient(String baseUrl, String token) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultStatusHandler(status -> true, (request, response) -> {
                    // Status assertions happen in the tests, never as exceptions.
                });
        if (token != null) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        this.client = builder.build();
    }

    public static ApiClient as(String baseUrl, UUID userId, String email) {
        return new ApiClient(baseUrl, TestJwtFactory.token(userId, email));
    }

    public static ApiClient as(String baseUrl, UUID userId, String email,
                               java.util.List<String> roles) {
        return new ApiClient(baseUrl, TestJwtFactory.token(userId, email, roles));
    }

    public static ApiClient as(String baseUrl, UUID userId, String email,
                               java.util.List<String> roles, java.util.List<String> permissions) {
        return new ApiClient(baseUrl, TestJwtFactory.token(userId, email, roles, permissions));
    }

    public static ApiClient anonymous(String baseUrl) {
        return new ApiClient(baseUrl, null);
    }

    public Response get(String uri) {
        return exchange(client.get().uri(uri).retrieve().toEntity(String.class));
    }

    public Response post(String uri, Object body) {
        return exchange(client.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? "{}" : body)
                .retrieve().toEntity(String.class));
    }

    public Response put(String uri, Object body) {
        return exchange(client.put().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? "{}" : body)
                .retrieve().toEntity(String.class));
    }

    public Response patch(String uri, Object body) {
        return exchange(client.patch().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? "{}" : body)
                .retrieve().toEntity(String.class));
    }

    public Response delete(String uri) {
        return exchange(client.delete().uri(uri).retrieve().toEntity(String.class));
    }

    private Response exchange(ResponseEntity<String> entity) {
        try {
            String raw = entity.getBody();
            JsonNode body = raw == null || raw.isBlank()
                    ? JSON.nullNode()
                    : JSON.readTree(raw);
            return new Response(entity.getStatusCode().value(), body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse response body", e);
        }
    }
}
