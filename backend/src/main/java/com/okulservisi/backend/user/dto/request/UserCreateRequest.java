package com.okulservisi.backend.user.dto.request;

import com.okulservisi.backend.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * docs/API.md: "role zorunlu, sunucu geçici şifre üretir veya ADMIN belirler, passwordHash
 * istemciden alınmaz". {@code password} boş bırakılırsa sunucu rastgele bir geçici şifre üretir.
 */
public record UserCreateRequest(
        @NotBlank(message = "Ad soyad zorunludur.") String fullName,
        @NotBlank(message = "E-posta zorunludur.") @Email(message = "Geçerli bir e-posta giriniz.") String email,
        String phone,
        @NotNull(message = "Rol zorunludur.") Role role,
        @Size(min = 8, message = "Şifre en az 8 karakter olmalıdır.") String password) {
}
