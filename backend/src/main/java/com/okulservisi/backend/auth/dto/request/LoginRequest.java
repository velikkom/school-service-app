package com.okulservisi.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code rememberMe}: opsiyonel, null/false ise "hatırlanmıyor" sayılır — SADECE refresh token
 * (ve onunla aynı kaderi paylaşan BFF çerezlerinin) ömrünü etkiler (bkz. docs/SECURITY.md "Beni
 * hatırla"). Access token ömrü bundan bağımsız, her durumda sabittir.
 */
public record LoginRequest(
        @NotBlank(message = "E-posta zorunludur.") @Email(message = "Geçerli bir e-posta giriniz.") String email,
        @NotBlank(message = "Şifre zorunludur.") String password,
        Boolean rememberMe) {
}
