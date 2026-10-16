package com.sophia.documentworkspace.domain.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void tokensAreUrlSafeAndUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = TokenHasher.generateToken();
            assertThat(token).matches("^[A-Za-z0-9_-]+$");
            assertThat(tokens.add(token)).isTrue();
        }
    }

    @Test
    void sha256IsDeterministicAndHex() {
        String hash = TokenHasher.sha256Hex("value");
        assertThat(hash).hasSize(64).matches("^[0-9a-f]+$");
        assertThat(TokenHasher.sha256Hex("value")).isEqualTo(hash);
        assertThat(TokenHasher.sha256Hex("other")).isNotEqualTo(hash);
    }
}
