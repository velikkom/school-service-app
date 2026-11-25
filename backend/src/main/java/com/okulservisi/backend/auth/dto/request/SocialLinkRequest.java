package com.okulservisi.backend.auth.dto.request;

import com.okulservisi.backend.auth.entity.OAuthProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialLinkRequest(
        @NotNull(message = "provider zorunludur.") OAuthProvider provider,
        @NotBlank(message = "providerUserId zorunludur.") String providerUserId,
        @Email(message = "Geçerli bir e-posta giriniz.") String email) {
}
