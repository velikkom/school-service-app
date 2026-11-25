package com.okulservisi.backend.auth.dto.response;

/** {@link SocialLoginResponse#status()} — docs/SECURITY.md "Sosyal giriş" üç senaryo. */
public enum SocialLoginStatus {
    AUTHENTICATED,
    PENDING_APPROVAL,
    NEEDS_LINKING
}
