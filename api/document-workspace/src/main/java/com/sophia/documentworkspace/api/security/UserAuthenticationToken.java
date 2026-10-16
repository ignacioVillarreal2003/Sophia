package com.sophia.documentworkspace.api.security;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * Authentication carrying the domain-level {@link AuthenticatedUser} as principal
 * instead of the raw JWT, so the rest of the codebase never touches token internals.
 */
public class UserAuthenticationToken extends AbstractAuthenticationToken {

    private final transient AuthenticatedUser principal;
    private final transient Jwt token;

    public UserAuthenticationToken(AuthenticatedUser principal,
                                   Jwt token,
                                   Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.id().toString();
    }
}
