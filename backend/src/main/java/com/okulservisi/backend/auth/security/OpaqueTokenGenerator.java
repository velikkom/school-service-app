package com.okulservisi.backend.auth.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Refresh/reset-password token'ları için kriptografik olarak rastgele, opak (JWT olmayan) token
 * üretimi ve SHA-256 hash hesaplama. Ham token sadece istemciye bir kez döner/e-posta ile
 * gönderilir; DB'de ve loglarda asla saklanmaz — sadece hash'i tutulur (bkz. docs/SECURITY.md).
 */
@Component
public class OpaqueTokenGenerator {

    private static final int TOKEN_BYTES = 48;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }
}
