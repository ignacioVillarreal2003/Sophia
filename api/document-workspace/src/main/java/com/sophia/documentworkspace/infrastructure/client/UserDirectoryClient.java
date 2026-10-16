package com.sophia.documentworkspace.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sophia.documentworkspace.domain.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves platform users through the authentication service, forwarding the caller's own
 * access token. The base URL is derived from the configured JSON Web Key Set URI.
 */
@Component
public class UserDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(UserDirectoryClient.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserSummary(UUID id, String email, String fullName, String photoUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope(UserSummary data) {
    }

    private final RestClient client;

    public UserDirectoryClient(
            @Value("${sophia.security.jwt.json-web-key-set-uri:}") String jsonWebKeySetUri,
            @Value("${sophia.user-directory.base-url:}") String baseUrl) {
        String resolved = baseUrl;
        if (resolved == null || resolved.isBlank()) {
            resolved = deriveBase(jsonWebKeySetUri);
        }
        this.client = resolved.isBlank() ? null : RestClient.builder().baseUrl(resolved).build();
    }

    static String deriveBase(String jsonWebKeySetUri) {
        if (jsonWebKeySetUri == null || jsonWebKeySetUri.isBlank()) {
            return "";
        }
        URI uri = URI.create(jsonWebKeySetUri);
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    /** The caller's own Authorization header, forwarded as-is so the directory sees the same identity. */
    private static String currentAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String header = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        throw new ServiceUnavailableException("No access token to forward to the user directory");
    }

    public Optional<UserSummary> findByEmail(String email) {
        if (client == null) {
            throw new ServiceUnavailableException("User directory is not configured");
        }
        String authorization = currentAuthorizationHeader();
        try {
            Envelope envelope = client.get()
                    .uri(builder -> builder.path("/api/v1/users/lookup").queryParam("email", email).build())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> { })
                    .body(Envelope.class);
            return Optional.ofNullable(envelope).map(Envelope::data).filter(u -> u.id() != null);
        } catch (RuntimeException ex) {
            log.warn("User directory lookup failed: {}", ex.getMessage());
            throw new ServiceUnavailableException("The user directory is unavailable");
        }
    }
}
