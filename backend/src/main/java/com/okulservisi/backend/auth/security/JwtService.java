package com.okulservisi.backend.auth.security;

import com.okulservisi.backend.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Access token (JWT) üretimi/doğrulaması. jjwt 0.12.6 API'si kullanılır. */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";

    private final JwtProperties jwtProperties;

    private javax.crypto.SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.resolveKeyBytes());
    }

    public String generateAccessToken(Long userId, String email, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getAccessTokenTtlMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return jwtProperties.getAccessTokenTtlMinutes() * 60;
    }

    /** @return geçerliyse claims; süresi dolmuş/imzası geçersizse boş. */
    public java.util.Optional<Claims> parseClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return java.util.Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public Role extractRole(Claims claims) {
        return Role.valueOf(claims.get(CLAIM_ROLE, String.class));
    }
}
