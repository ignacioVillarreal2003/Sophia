package com.sophia.authentication.api.controller;

import com.sophia.authentication.application.security.JsonWebTokenKeyManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Discovery", description = "Public key discovery for token validation")
public class JsonWebKeySetController {

    private final JsonWebTokenKeyManager keyManager;

    @GetMapping("/.well-known/json-web-key-set.json")
    @Operation(summary = "JSON Web Key Set (RFC 7517)",
            description = "Public keys used to verify JWTs issued by this service. "
                    + "Match tokens to keys through the `kid` header.")
    public ResponseEntity<Map<String, Object>> getJsonWebKeySet() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(Map.of("keys", List.of(keyManager.toPublicJsonWebKey())));
    }
}
