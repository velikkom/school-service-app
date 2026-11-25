package com.okulservisi.backend.auth.dto.request;

import com.okulservisi.backend.auth.entity.OAuthProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * BFF→backend (server-to-server) ile sağlayıcıdan ZATEN doğrulanmış kimlik bilgisi (bkz.
 * docs/SECURITY.md "Sosyal giriş" — bu endpoint doğrudan tarayıcıdan çağrılamaz sayılır, backend
 * bu veriye asla ham/doğrulanmamış şekilde güvenmez, kendi tarafında provider+providerUserId
 * eşleşmesini {@code oauth_identity} ile kontrol eder). Sağlayıcının userinfo endpoint'i
 * {@code email} alanını her zaman döner (bkz. docs/SECURITY.md adım 2) — bu yüzden zorunludur.
 */
public record SocialLoginRequest(
        @NotNull(message = "provider zorunludur.") OAuthProvider provider,
        @NotBlank(message = "providerUserId zorunludur.") String providerUserId,
        @NotBlank(message = "E-posta zorunludur.") @Email(message = "Geçerli bir e-posta giriniz.") String email,
        boolean emailVerified,
        String fullName) {
}
