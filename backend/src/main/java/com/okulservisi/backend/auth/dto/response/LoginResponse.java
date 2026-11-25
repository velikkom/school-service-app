package com.okulservisi.backend.auth.dto.response;

import java.time.Instant;

/**
 * {@code refreshTokenExpiresAt}: refresh token'ın gerçek bitiş zamanı — BFF, "Beni hatırla"
 * çerezinin {@code Max-Age}'ini BU değerden türetir, ayrı bir sabitten DEĞİL (bkz. docs/SECURITY.md
 * "Beni hatırla").
 */
public record LoginResponse(String accessToken, String refreshToken, Instant refreshTokenExpiresAt, UserSummaryDto user) {
}
