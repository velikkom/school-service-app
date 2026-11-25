package com.okulservisi.backend.auth.service.impl;

import com.okulservisi.backend.auth.entity.RefreshToken;
import com.okulservisi.backend.auth.repository.RefreshTokenRepository;
import com.okulservisi.backend.auth.dto.request.LoginRequest;
import com.okulservisi.backend.auth.dto.response.LoginResponse;
import com.okulservisi.backend.auth.dto.request.LogoutRequest;
import com.okulservisi.backend.auth.dto.response.MeResponse;
import com.okulservisi.backend.auth.dto.request.RefreshRequest;
import com.okulservisi.backend.auth.dto.response.RefreshResponse;
import com.okulservisi.backend.auth.dto.response.UserSummaryDto;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.auth.service.AuthService;
import com.okulservisi.backend.auth.service.RateLimiterService;
import com.okulservisi.backend.auth.security.JwtProperties;
import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.auth.security.OpaqueTokenGenerator;
import com.okulservisi.backend.common.exception.TooManyRequestsException;
import com.okulservisi.backend.common.exception.UnauthorizedException;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oturum/token yaşam döngüsü: login/refresh/logout/me. Parola yönetimi (changePassword/
 * forgotPassword/resetPassword) {@link PasswordManagementService}'te — iki bağımsız sorumluluk,
 * ayrı sınıf (mimari bulgu #8).
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String GENERIC_LOGIN_ERROR = "E-posta veya şifre hatalı.";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final RateLimiterService rateLimiterService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        if (!rateLimiterService.tryConsume(clientIp, request.email())) {
            throw new TooManyRequestsException("Çok fazla deneme yapıldı, lütfen daha sonra tekrar deneyin.");
        }
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException(GENERIC_LOGIN_ERROR));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(GENERIC_LOGIN_ERROR);
        }
        rateLimiterService.reset(clientIp, request.email());
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
        IssuedRefreshToken issued = issueRefreshToken(user, rememberMe);
        UserSummaryDto summary = new UserSummaryDto(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
        return new LoginResponse(accessToken, issued.rawToken(), issued.expiresAt(), summary);
    }

    @Override
    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        String tokenHash = opaqueTokenGenerator.hash(request.refreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Geçersiz oturum, lütfen tekrar giriş yapın."));
        if (!refreshToken.isActive(Instant.now()) || !refreshToken.getUser().isActive()) {
            throw new UnauthorizedException("Oturumunuzun süresi doldu, lütfen tekrar giriş yapın.");
        }
        User user = refreshToken.getUser();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        return new RefreshResponse(accessToken);
    }

    @Override
    @Transactional
    public void logout(AuthenticatedUser principal, LogoutRequest request) {
        String tokenHash = opaqueTokenGenerator.hash(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
            if (!rt.getUser().getId().equals(principal.getUserId())) {
                // Başka bir kullanıcının token'ı — sessizce yok say (bilgi sızdırmadan no-op).
                return;
            }
            refreshTokenRepository.revokeByTokenHash(tokenHash, Instant.now());
        });
    }

    @Override
    public MeResponse me(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı."));
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getRole());
    }

    /**
     * {@link SocialAuthService} tarafından, zaten bağlı bir {@code oauth_identity} için normal
     * {@code login()} ile AYNI token üretimini tekrar etmeden kullanır (bkz. docs/SECURITY.md
     * "Sosyal giriş"). Sosyal girişte "beni hatırla" checkbox'ı YOKTUR, her zaman en kısa/güvenli
     * tavan ({@code rememberMe=false}) uygulanır. Rate limit burada uygulanmaz — bu çağrı sadece
     * provider tarafından ZATEN doğrulanmış, DB'de önceden bağlanmış bir kimlik için çalışır.
     */
    @Override
    @Transactional
    public LoginResponse issueTokensForUser(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        IssuedRefreshToken issued = issueRefreshToken(user, false);
        UserSummaryDto summary = new UserSummaryDto(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
        return new LoginResponse(accessToken, issued.rawToken(), issued.expiresAt(), summary);
    }

    /**
     * @param rememberMe true ise {@code refreshTokenTtlDaysRememberMe} (varsayılan 30 gün),
     *                   değilse {@code refreshTokenTtlDays} (varsayılan 1 gün) kullanılır
     *                   (bkz. docs/SECURITY.md "Beni hatırla").
     */
    private IssuedRefreshToken issueRefreshToken(User user, boolean rememberMe) {
        String rawToken = opaqueTokenGenerator.generate();
        long ttlDays = rememberMe ? jwtProperties.getRefreshTokenTtlDaysRememberMe() : jwtProperties.getRefreshTokenTtlDays();
        Instant expiresAt = Instant.now().plus(ttlDays, ChronoUnit.DAYS);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(opaqueTokenGenerator.hash(rawToken));
        refreshToken.setExpiresAt(expiresAt);
        refreshTokenRepository.save(refreshToken);
        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    private record IssuedRefreshToken(String rawToken, Instant expiresAt) {
    }
}
