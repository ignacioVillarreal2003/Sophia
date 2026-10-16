package com.sophia.authentication.infrastructure.security.oauth2;

public record GoogleUserInfo(
        String subject,
        String email,
        boolean emailVerified,
        String firstName,
        String lastName,
        String pictureUrl) {
}
