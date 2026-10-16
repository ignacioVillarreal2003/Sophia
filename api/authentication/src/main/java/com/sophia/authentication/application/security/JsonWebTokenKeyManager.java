package com.sophia.authentication.application.security;

import com.sophia.authentication.domain.config.ApplicationProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Getter
@Slf4j
public class JsonWebTokenKeyManager {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    public JsonWebTokenKeyManager(ApplicationProperties properties) {
        String configured = properties.security().jwt().privateKey();
        KeyPair pair;
        if (StringUtils.hasText(configured)) {
            pair = loadKeyPair(configured.trim());
            log.info("Loaded configured RSA signing key");
        } else {
            pair = generateEphemeral();
            log.warn("JWT_PRIVATE_KEY is not configured; generated an EPHEMERAL RSA key pair. "
                    + "Issued tokens will not survive a restart. Never run production like this.");
        }
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey = (RSAPublicKey) pair.getPublic();
        this.keyId = deriveKeyId(this.publicKey);
    }

    public Map<String, Object> toPublicJsonWebKey() {
        Map<String, Object> jsonWebKey = new LinkedHashMap<>();
        jsonWebKey.put("kty", "RSA");
        jsonWebKey.put("use", "sig");
        jsonWebKey.put("alg", "RS256");
        jsonWebKey.put("kid", keyId);
        jsonWebKey.put("n", base64Url(publicKey.getModulus()));
        jsonWebKey.put("e", base64Url(publicKey.getPublicExponent()));
        return jsonWebKey;
    }

    private static KeyPair loadKeyPair(String configured) {
        try {
            byte[] der = toPkcs8Der(configured);
            RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException("JWT private key must be a full RSA CRT key (PKCS#8)");
            }
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
            return new KeyPair(publicKey, key);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Invalid JWT_PRIVATE_KEY: expected an RSA PKCS#8 key as PEM, base64 PEM or base64 DER", e);
        }
    }

    private static byte[] toPkcs8Der(String configured) {
        String value = configured;
        if (!value.contains("-----BEGIN")) {
            byte[] decoded = Base64.getMimeDecoder().decode(value.replaceAll("\\s", ""));
            String asText = new String(decoded, StandardCharsets.US_ASCII);
            if (asText.contains("-----BEGIN")) {
                value = asText;
            } else {
                return decoded;
            }
        }
        String base64 = value
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static KeyPair generateEphemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate an RSA key pair", e);
        }
    }

    private static String deriveKeyId(RSAPublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(Arrays.copyOf(digest, 8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive JWT key id", e);
        }
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
