package com.sophia.documentworkspace.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicLinkTest {

    private PublicLink link(Instant expiresAt) {
        return PublicLink.create(UUID.randomUUID(), UUID.randomUUID(), "hash", UUID.randomUUID(), expiresAt);
    }

    @Test
    void aLinkWithoutExpiryIsActiveUntilRevoked() {
        PublicLink link = link(null);

        assertThat(link.isActive(Instant.now())).isTrue();
        link.revoke();
        assertThat(link.isActive(Instant.now())).isFalse();
    }

    @Test
    void aLinkStopsWorkingAtItsExpiry() {
        Instant now = Instant.now();
        PublicLink link = link(now.plusSeconds(60));

        assertThat(link.isActive(now)).isTrue();
        assertThat(link.isActive(now.plusSeconds(61))).isFalse();
    }

    @Test
    void revokingTwiceKeepsTheFirstRevocationTime() {
        PublicLink link = link(null);
        link.revoke();
        Instant first = link.getRevokedAt();

        link.revoke();

        assertThat(link.getRevokedAt()).isEqualTo(first);
    }
}
