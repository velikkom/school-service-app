package com.okulservisi.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "E-posta zorunludur.") @Email(message = "Geçerli bir e-posta giriniz.") String email) {
}
