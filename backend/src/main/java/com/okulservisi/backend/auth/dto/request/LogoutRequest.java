package com.okulservisi.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank(message = "refreshToken zorunludur.") String refreshToken) {
}
