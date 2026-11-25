package com.okulservisi.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(@NotBlank(message = "token zorunludur.") String token) {
}
