package com.sophia.authentication.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Confirm account deletion. Password is required for local accounts; "
        + "accounts created through an OAuth provider may omit it.")
public record DeleteAccountRequest(String password) {

}
