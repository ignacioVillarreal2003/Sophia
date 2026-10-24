package com.sophia.ai.api.security;

import com.sophia.ai.application.security.AuthenticatedUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

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
