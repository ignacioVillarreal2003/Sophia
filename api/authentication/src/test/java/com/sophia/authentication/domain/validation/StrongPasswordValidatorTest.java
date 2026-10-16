package com.sophia.authentication.domain.validation;

import com.sophia.authentication.domain.validation.StrongPasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"S3cure!Pass", "Another#1x", "Aa1!aaaa"})
    void acceptsStrongPasswords(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short1!",          // too short
            "alllowercase1!",   // no uppercase
            "ALLUPPERCASE1!",   // no lowercase
            "NoDigits!!aa",     // no digit
            "NoSpecial11aa"     // no special char
    })
    void rejectsWeakPasswords(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void nullIsDelegatedToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void rejectsOverlongPasswords() {
        String overlong = "Aa1!" + "x".repeat(130);
        assertThat(validator.isValid(overlong, null)).isFalse();
    }
}
