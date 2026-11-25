package com.okulservisi.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * docs/API.md "1. Auth" / docs/SECURITY.md "Kayıt talebi ve ADMIN onayı". {@code role}/
 * {@code isActive}/{@code approved} alanları BİLEREK YOKTUR — bu alanları her zaman ADMIN,
 * ayrı bir onay adımında belirler.
 */
public record RegistrationRequestCreateRequest(
        @NotBlank(message = "Ad zorunludur.") String firstName,
        @NotBlank(message = "Soyad zorunludur.") String lastName,
        @NotBlank(message = "E-posta zorunludur.") @Email(message = "Geçerli bir e-posta giriniz.") String email,
        @NotBlank(message = "Şifre zorunludur.") @Size(min = 8, message = "Şifre en az 8 karakter olmalıdır.") String password,
        @NotBlank(message = "Şifre tekrarı zorunludur.") String passwordConfirm) {
}
