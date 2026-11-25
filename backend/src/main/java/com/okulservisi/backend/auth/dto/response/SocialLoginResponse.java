package com.okulservisi.backend.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Üç olası senaryoyu TEK bir şekilde karşılar (bkz. docs/SECURITY.md "Sosyal giriş"):
 * <ul>
 *   <li>{@code AUTHENTICATED}: {@code accessToken}/{@code refreshToken}/{@code refreshTokenExpiresAt}/
 *   {@code user} dolu, {@code message} null.</li>
 *   <li>{@code PENDING_APPROVAL}: sadece {@code message} dolu, token alanları null (tarayıcıya TOKEN
 *   VERİLMEZ).</li>
 *   <li>{@code NEEDS_LINKING}: sadece {@code message} dolu, token alanları null.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResponse(
        SocialLoginStatus status,
        String accessToken,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserSummaryDto user,
        String message) {

    public static SocialLoginResponse authenticated(LoginResponse login) {
        return new SocialLoginResponse(SocialLoginStatus.AUTHENTICATED, login.accessToken(), login.refreshToken(),
                login.refreshTokenExpiresAt(), login.user(), null);
    }

    public static SocialLoginResponse pendingApproval(String message) {
        return new SocialLoginResponse(SocialLoginStatus.PENDING_APPROVAL, null, null, null, null, message);
    }

    public static SocialLoginResponse needsLinking(String message) {
        return new SocialLoginResponse(SocialLoginStatus.NEEDS_LINKING, null, null, null, null, message);
    }
}
