package com.okulservisi.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mevcut şifre zorunludur.") String currentPassword,
        @NotBlank(message = "Yeni şifre zorunludur.") @Size(min = 8, message = "Yeni şifre en az 8 karakter olmalıdır.") String newPassword) {
}
