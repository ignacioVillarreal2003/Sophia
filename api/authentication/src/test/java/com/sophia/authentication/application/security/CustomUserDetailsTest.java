package com.sophia.authentication.application.security;

import com.sophia.authentication.application.security.CustomUserDetails;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsTest {

    @Test
    @DisplayName("built from a User entity exposes its roles as authorities and honours lock/active state")
    void fromUserEntity() {
        var user = TestFixtures.user("jane@test.local", "hash");
        user.lock(Instant.now().plusSeconds(600));
        user.updateActiveStatus(false);

        CustomUserDetails details = CustomUserDetails.from(user);

        assertThat(details.getUsername()).isEqualTo("jane@test.local");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.isEnabled()).isFalse();
        assertThat(details.isAccountNonLocked()).isFalse();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_USER");
    }

    @Test
    @DisplayName("built from a JWT carries the session id and given authorities without a password")
    void fromJsonWebToken() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        List<GrantedAuthority> authorities = List.of(() -> "ROLE_ADMIN");

        CustomUserDetails details = CustomUserDetails.fromJsonWebToken(userId, "jane@test.local", sessionId, authorities);

        assertThat(details.getId()).isEqualTo(userId);
        assertThat(details.getSessionId()).isEqualTo(sessionId);
        assertThat(details.getPassword()).isNull();
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }
}
