package com.sophia.documentworkspace.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mints HS256 JWTs equivalent to the ones the authentication service issues,
 * signed with the shared integration-test secret.
 */
public final class TestJwtFactory {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TestJwtFactory() {
    }

    public static String token(UUID userId, String email, List<String> roles, List<String> permissions) {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId.toString());
        claims.put("email", email);
        claims.put("roles", roles);
        claims.put("permissions", permissions);
        claims.put("iss", "sophia-authentication");
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());

        String payload = encode(header) + "." + encode(claims);
        return payload + "." + sign(payload);
    }

    public static String token(UUID userId, String email, List<String> roles) {
        return token(userId, email, roles, List.of());
    }

    public static String token(UUID userId, String email) {
        return token(userId, email, List.of(), List.of());
    }

    private static String encode(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(JSON.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    AbstractIntegrationTest.JWT_SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
