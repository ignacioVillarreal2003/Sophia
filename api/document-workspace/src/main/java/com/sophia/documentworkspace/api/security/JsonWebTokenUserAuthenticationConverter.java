package com.sophia.documentworkspace.api.security;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps the JWT issued by the authentication service to a {@link UserAuthenticationToken}.
 * Expected claims: {@code sub} = user UUID, {@code email}, {@code roles} = global roles,
 * {@code permissions} = the caller's flattened effective permission names.
 */
public class JsonWebTokenUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseSubject(jwt);
        String email = jwt.getClaimAsString(CLAIM_EMAIL);
        List<String> roles = jwt.getClaimAsStringList(CLAIM_ROLES);
        Set<String> globalRoles = roles == null ? Set.of() : Set.copyOf(roles);
        List<String> permissions = jwt.getClaimAsStringList(CLAIM_PERMISSIONS);
        Set<String> effectivePermissions = permissions == null ? Set.of() : Set.copyOf(permissions);

        Set<GrantedAuthority> authorities = Stream.concat(
                        globalRoles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                        effectivePermissions.stream().map(SimpleGrantedAuthority::new))
                .collect(Collectors.toUnmodifiableSet());

        AuthenticatedUser principal = new AuthenticatedUser(userId, email, globalRoles, effectivePermissions);
        return new UserAuthenticationToken(principal, jwt, authorities);
    }

    private UUID parseSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new BadJwtException("Token is missing the sub claim");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new BadJwtException("Token sub claim is not a valid UUID", e);
        }
    }
}
