package com.sophia.authentication.application.security;

import com.sophia.authentication.application.security.JsonWebTokenKeyManager;
import com.sophia.authentication.application.security.JsonWebTokenService;
import com.sophia.authentication.domain.entity.ServiceClient;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.support.TestFixtures;
import com.sophia.authentication.support.TestIds;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonWebTokenServiceTest {

    private JsonWebTokenKeyManager keyManager;
    private JsonWebTokenService jsonWebTokenService;
    private User user;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        keyManager = new JsonWebTokenKeyManager(TestFixtures.appProperties());
        jsonWebTokenService = new JsonWebTokenService(TestFixtures.appProperties(), keyManager);
        user = TestFixtures.user("jane@test.local", "hash");
        sessionId = UUID.randomUUID();
    }

    @Test
    @DisplayName("generates a user token whose claims round-trip")
    void generateAndParse() {
        String token = jsonWebTokenService.generateAccessToken(user, sessionId);

        Claims claims = jsonWebTokenService.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get(JsonWebTokenService.CLAIM_EMAIL, String.class)).isEqualTo("jane@test.local");
        assertThat(claims.get(JsonWebTokenService.CLAIM_SESSION_ID, String.class)).isEqualTo(sessionId.toString());
        assertThat(claims.get(JsonWebTokenService.CLAIM_TYPE, String.class)).isEqualTo(JsonWebTokenService.TYPE_ACCESS);
        assertThat(claims.get(JsonWebTokenService.CLAIM_ROLES, List.class)).contains("USER");
    }

    @Test
    @DisplayName("generates a service token carrying the client scopes")
    void generateServiceToken() {
        ServiceClient client = TestIds.withRandomId(
                ServiceClient.create("billing-service", null, "hash", List.of("USER_READ"), true));

        Claims claims = jsonWebTokenService.parseAndValidate(jsonWebTokenService.generateServiceToken(client));

        assertThat(claims.getSubject()).isEqualTo(client.getId().toString());
        assertThat(claims.get(JsonWebTokenService.CLAIM_TYPE, String.class)).isEqualTo(JsonWebTokenService.TYPE_SERVICE);
        assertThat(claims.get(JsonWebTokenService.CLAIM_CLIENT_NAME, String.class)).isEqualTo("billing-service");
        assertThat(claims.get(JsonWebTokenService.CLAIM_SCOPES, List.class)).containsExactly("USER_READ");
    }

    @Test
    @DisplayName("rejects tokens signed with a different key pair")
    void rejectsForeignSignature() {
        JsonWebTokenService foreign = new JsonWebTokenService(TestFixtures.appProperties(),
                new JsonWebTokenKeyManager(TestFixtures.appProperties()));

        String token = foreign.generateAccessToken(user, sessionId);

        assertThatThrownBy(() -> jsonWebTokenService.parseAndValidate(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rejects garbage tokens")
    void rejectsGarbage() {
        assertThatThrownBy(() -> jsonWebTokenService.parseAndValidate("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("loads a configured PKCS#8 key and keeps a stable key id")
    void loadsConfiguredKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String base64Der = Base64.getEncoder()
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());

        JsonWebTokenKeyManager first = new JsonWebTokenKeyManager(TestFixtures.appProperties(base64Der));
        JsonWebTokenKeyManager second = new JsonWebTokenKeyManager(TestFixtures.appProperties(base64Der));

        assertThat(first.getKeyId()).isEqualTo(second.getKeyId());
        JsonWebTokenService signer = new JsonWebTokenService(TestFixtures.appProperties(base64Der), first);
        JsonWebTokenService verifier = new JsonWebTokenService(TestFixtures.appProperties(base64Der), second);
        Claims claims = verifier.parseAndValidate(signer.generateAccessToken(user, sessionId));
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
    }

    @Test
    @DisplayName("rejects an invalid configured key at startup")
    void rejectsInvalidConfiguredKey() {
        assertThatThrownBy(() -> new JsonWebTokenKeyManager(TestFixtures.appProperties("not-a-key")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("publishes a well-formed public JSON Web Key")
    void publishesJsonWebKey() {
        var jsonWebKey = keyManager.toPublicJsonWebKey();

        assertThat(jsonWebKey).containsEntry("kty", "RSA")
                .containsEntry("alg", "RS256")
                .containsEntry("use", "sig")
                .containsKeys("kid", "n", "e");
        assertThat(String.valueOf(jsonWebKey.get("n"))).doesNotContain("=", "+", "/");
    }
}
