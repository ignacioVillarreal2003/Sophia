package com.sophia.authentication.application.security;

import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.domain.entity.Role;
import com.sophia.authentication.domain.entity.ServiceClient;
import com.sophia.authentication.domain.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JsonWebTokenService {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_SESSION_ID = "sid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_SCOPES = "scopes";
    public static final String CLAIM_CLIENT_NAME = "client_name";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_SERVICE = "service";

    private final JsonWebTokenKeyManager keyManager;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration serviceTokenTtl;

    public JsonWebTokenService(ApplicationProperties properties, JsonWebTokenKeyManager keyManager) {
        ApplicationProperties.Security.JsonWebToken jsonWebToken = properties.security().jwt();
        this.keyManager = keyManager;
        this.issuer = jsonWebToken.issuer();
        this.accessTokenTtl = jsonWebToken.accessTokenTtl();
        this.serviceTokenTtl = jsonWebToken.serviceTokenTtl();
    }

    public String generateAccessToken(User user, UUID sessionId) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        List<String> permissions = List.copyOf(user.getEffectivePermissions());
        return Jwts.builder()
                .header().keyId(keyManager.getKeyId()).and()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_SESSION_ID, sessionId.toString())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_PERMISSIONS, permissions)
                .signWith(keyManager.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public String generateServiceToken(ServiceClient client) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keyManager.getKeyId()).and()
                .id(UUID.randomUUID().toString())
                .subject(client.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(serviceTokenTtl)))
                .claim(CLAIM_TYPE, TYPE_SERVICE)
                .claim(CLAIM_CLIENT_NAME, client.getName())
                .claim(CLAIM_SCOPES, List.copyOf(client.getScopes()))
                .signWith(keyManager.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(keyManager.getPublicKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    public long getServiceTokenTtlSeconds() {
        return serviceTokenTtl.toSeconds();
    }
}
