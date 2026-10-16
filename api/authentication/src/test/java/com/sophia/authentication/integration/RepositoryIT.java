package com.sophia.authentication.integration;

import com.sophia.authentication.infrastructure.config.JpaAuditingConfiguration;
import com.sophia.authentication.domain.entity.RefreshToken;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.domain.entity.UserSession;
import com.sophia.authentication.domain.entity.enums.AuthenticationProvider;
import com.sophia.authentication.infrastructure.repository.RefreshTokenRepository;
import com.sophia.authentication.infrastructure.repository.RoleRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.repository.UserSessionRepository;
import com.sophia.authentication.domain.util.TokenHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Repository slice tests against real PostgreSQL (Flyway migrations included). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfiguration.class)
@Testcontainers
@DisplayName("Repositories on PostgreSQL")
class RepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserSessionRepository userSessionRepository;
    @Autowired private TestEntityManager entityManager;

    private User persistUser(String email) {
        User user = User.create(
                "Jane", "Doe", email, "hash", AuthenticationProvider.LOCAL, true, false,
                new HashSet<>(Set.of(roleRepository.findByName("USER").orElseThrow())));
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("migrations seed the two default roles and the permission catalog")
    void seededRoles() {
        assertThat(roleRepository.findAll()).extracting("name")
                .contains("ADMIN", "USER");
        assertThat(roleRepository.findByName("ADMIN").orElseThrow().getPermissions())
                .extracting("name").contains("USER_READ", "ROLE_MANAGE", "SYSTEM_SETTINGS");
    }

    @Test
    @DisplayName("email uniqueness is enforced case-insensitively")
    void emailUniqueness() {
        persistUser("unique@test.local");

        assertThatThrownBy(() -> persistUser("UNIQUE@test.local"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("soft-deleted users disappear from queries and free their email")
    void softDelete() {
        User user = persistUser("softdelete@test.local");
        user.softDelete("admin@test.local");
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByEmailIgnoreCase("softdelete@test.local")).isEmpty();
        assertThat(userRepository.existsByEmailIgnoreCase("softdelete@test.local")).isFalse();

        // The email can be registered again
        persistUser("softdelete@test.local");
    }

    @Test
    @DisplayName("findByEmailWithAuthorities loads roles and permissions eagerly")
    void authoritiesEagerlyLoaded() {
        persistUser("authorities@test.local");

        User loaded = userRepository.findByEmailWithAuthorities("authorities@test.local").orElseThrow();

        assertThat(loaded.getRoles()).hasSize(1);
        // USER gets baseline self-service notification permissions by default; see R__roles_and_permissions.sql
        assertThat(loaded.getEffectivePermissions()).containsExactlyInAnyOrder(
                "NOTIFICATION_READ", "NOTIFICATION_UPDATE", "NOTIFICATION_DELETE",
                "NOTIFICATION_PREFERENCE_READ", "NOTIFICATION_PREFERENCE_UPDATE");
    }

    @Test
    @DisplayName("bulk revocation touches only the targeted user's tokens")
    void bulkRevocationScoped() {
        User alice = persistUser("alice@test.local");
        User bob = persistUser("bob@test.local");
        UserSession aliceSession = userSessionRepository.saveAndFlush(
                UserSession.create(alice, null, null, null, false, null, Instant.now().plusSeconds(3600)));
        UserSession bobSession = userSessionRepository.saveAndFlush(
                UserSession.create(bob, null, null, null, false, null, Instant.now().plusSeconds(3600)));
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                TokenHasher.sha256Hex("alice-token"), alice, aliceSession, Instant.now().plusSeconds(3600),
                null, null));
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                TokenHasher.sha256Hex("bob-token"), bob, bobSession, Instant.now().plusSeconds(3600), null, null));

        int revoked = refreshTokenRepository.revokeAllByUserId(alice.getId(), Instant.now());
        entityManager.clear();

        assertThat(revoked).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex("alice-token"))
                .orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex("bob-token"))
                .orElseThrow().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("optimistic locking bumps the version on every update")
    void optimisticLocking() {
        User user = persistUser("version@test.local");
        long initialVersion = user.getVersion();

        user.updateProfile("Janet", null);
        userRepository.saveAndFlush(user);

        assertThat(user.getVersion()).isGreaterThan(initialVersion);
    }
}
