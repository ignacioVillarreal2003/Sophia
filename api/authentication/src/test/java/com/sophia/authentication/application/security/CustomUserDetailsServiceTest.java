package com.sophia.authentication.application.security;

import com.sophia.authentication.application.security.CustomUserDetails;
import com.sophia.authentication.application.security.CustomUserDetailsService;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    @DisplayName("loads a user with its authorities")
    void loadsExistingUser() {
        var user = TestFixtures.user("jane@test.local", "hash");
        when(userRepository.findByEmailWithAuthorities("jane@test.local")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("jane@test.local");

        assertThat(details).isInstanceOf(CustomUserDetails.class);
        assertThat(details.getUsername()).isEqualTo("jane@test.local");
        assertThat(details.getAuthorities()).extracting(Object::toString).contains("ROLE_USER");
    }

    @Test
    @DisplayName("rejects unknown emails")
    void rejectsUnknownEmail() {
        when(userRepository.findByEmailWithAuthorities("ghost@test.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost@test.local"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
