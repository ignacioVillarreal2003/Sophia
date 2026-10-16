package com.sophia.authentication.application.security;

import com.sophia.authentication.domain.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Getter
public class CustomUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonLocked;

    private final UUID sessionId;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(UUID id,
                             String email,
                             String password,
                             boolean enabled,
                             boolean accountNonLocked,
                             UUID sessionId,
                             Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.sessionId = sessionId;
        this.authorities = authorities;
    }

    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.isActive(),
                !user.isLocked(),
                null,
                buildAuthorities(user));
    }

    public static CustomUserDetails fromJsonWebToken(UUID id,
                                            String email,
                                            UUID sessionId,
                                            Collection<? extends GrantedAuthority> authorities) {
        return new CustomUserDetails(id, email, null, true, true, sessionId, authorities);
    }

    private static List<GrantedAuthority> buildAuthorities(User user) {
        Stream<GrantedAuthority> roleAuthorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()));
        Stream<GrantedAuthority> permissionAuthorities = user.getEffectivePermissions().stream()
                .map(SimpleGrantedAuthority::new);
        return Stream.concat(roleAuthorities, permissionAuthorities).map(GrantedAuthority.class::cast).toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
