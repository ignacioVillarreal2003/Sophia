package com.sophia.ai.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Reads documents from the document workspace service with the caller's own token, so the AI service can never see
 * anything the person could not open themselves. Only text-bearing documents are supported: notes, checklists, card
 * boards, whiteboards (their text) and spreadsheets (as CSV). Uploaded files must be converted to a document first.
 */
@Slf4j
@Component
public class WorkspaceDocumentClient {

    /** A document's title and its text, as plain Markdown or CSV. */
    public record DocumentText(String title, String type, String text) {
    }

    private final RestClient client;

    public WorkspaceDocumentClient(ApplicationProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().baseUrl(properties.ai().workspaceApiUrl()).requestFactory(factory).build();
    }

    public DocumentText load(String authorization, UUID workspaceId, UUID artifactId) {
        String base = "/api/v1/workspaces/" + workspaceId + "/";
        JsonNode artifact = get(authorization, base + "artifacts/" + artifactId, JsonNode.class).path("data");
        String type = artifact.path("type").asText("");
        String title = artifact.path("name").asText("Untitled");
        String text = switch (type) {
            case "MARKDOWN_DOCUMENT", "CHECKLIST" ->
                    get(authorization, base + "documents/" + artifactId + "/content", JsonNode.class)
                            .path("data").path("content").asText("");
            case "PRESENTATION", "WHITEBOARD" -> export(authorization, base, artifactId, "MARKDOWN");
            case "SPREADSHEET" -> export(authorization, base, artifactId, "CSV");
            default -> throw new ApiException(ErrorCode.SOURCE_NOT_SUPPORTED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "\"%s\" cannot be used as a source. Convert it to an editable document first.".formatted(title));
        };
        return new DocumentText(title, type, text);
    }

    private String export(String authorization, String base, UUID artifactId, String format) {
        byte[] bytes = get(authorization, base + "artifacts/" + artifactId + "/export?format=" + format, byte[].class);
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private <T> T get(String authorization, String path, Class<T> type) {
        try {
            return client.get().uri(path).header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(type);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 403) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN,
                        "You do not have access to one of the selected documents");
            }
            if (e.getStatusCode().value() == 404) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "One of the selected documents no longer exists");
            }
            log.warn("Document service refused {}: {}", path, e.getStatusCode());
            throw new ApiException(ErrorCode.SOURCE_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "The documents could not be read right now");
        } catch (RestClientException e) {
            log.warn("Document service unreachable for {}", path, e);
            throw new ApiException(ErrorCode.SOURCE_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "The documents could not be read right now");
        }
    }
}
