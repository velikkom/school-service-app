package com.okulservisi.backend.auth.service.impl;

import com.okulservisi.backend.auth.entity.PasswordResetToken;
import com.okulservisi.backend.auth.repository.PasswordResetTokenRepository;
import com.okulservisi.backend.auth.dto.request.ChangePasswordRequest;
import com.okulservisi.backend.auth.dto.request.ForgotPasswordRequest;
import com.okulservisi.backend.auth.dto.request.ResetPasswordRequest;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.auth.service.PasswordManagementService;
import com.okulservisi.backend.auth.service.PasswordResetMailService;
import com.okulservisi.backend.auth.service.RateLimiterService;
import com.okulservisi.backend.auth.service.TokenRevocationService;
import com.okulservisi.backend.auth.security.OpaqueTokenGenerator;
import com.okulservisi.backend.common.exception.BadRequestException;
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
 * Parola yönetimi: changePassword/forgotPassword/resetPassword. Oturum/token yaşam döngüsü
 * (login/refresh/logout/me) {@link AuthService}'te — iki bağımsız sorumluluk, ayrı sınıf
 * (mimari bulgu #8). Sadece kendi ihtiyacı olan bağımlılıklar enjekte edilir; refresh token
 * iptali {@link TokenRevocationService} üzerinden yapıldığı için {@code RefreshTokenRepository}'ye
 * doğrudan gerek yoktur.
 */
@Service
@RequiredArgsConstructor
public class PasswordManagementServiceImpl implements PasswordManagementService {

    private static final String GENERIC_RESET_ERROR = "Token geçersiz veya süresi dolmuş.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final RateLimiterService rateLimiterService;
    private final TokenRevocationService tokenRevocationService;
    private final PasswordResetMailService passwordResetMailService;

    @Transactional
    @Override
    public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mevcut şifre hatalı.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        tokenRevocationService.revokeAllRefreshTokensForUser(user.getId());
    }

    @Transactional
    @Override
    public void forgotPassword(ForgotPasswordRequest request, String clientIp) {
        // docs/SECURITY.md: kullanıcı var/yok fark etmeksizin her zaman 202 — bu metot asla istisna fırlatmaz.
        if (!rateLimiterService.tryConsume(clientIp, request.email())) {
            throw new TooManyRequestsException("Çok fazla deneme yapıldı, lütfen daha sonra tekrar deneyin.");
        }
        userRepository.findByEmailIgnoreCase(request.email())
                .filter(User::isActive)
                .ifPresent(user -> {
                    tokenRevocationService.invalidateAllUnusedPasswordResetTokensForUser(user.getId());
                    String rawToken = opaqueTokenGenerator.generate();
                    PasswordResetToken resetToken = new PasswordResetToken();
                    resetToken.setUser(user);
                    resetToken.setTokenHash(opaqueTokenGenerator.hash(rawToken));
                    resetToken.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
                    passwordResetTokenRepository.save(resetToken);
                    passwordResetMailService.sendResetPasswordEmail(user.getEmail(), rawToken);
                });
    }

    @Transactional
    @Override
    public void resetPassword(ResetPasswordRequest request, String clientIp) {
        if (!rateLimiterService.tryConsume(clientIp, "reset:" + request.token().hashCode())) {
            throw new TooManyRequestsException("Çok fazla deneme yapıldı, lütfen daha sonra tekrar deneyin.");
        }
        String tokenHash = opaqueTokenGenerator.hash(request.token());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException(GENERIC_RESET_ERROR));
        if (resetToken.getExpiresAt().isBefore(Instant.now()) || resetToken.getUsedAt() != null) {
            throw new UnauthorizedException(GENERIC_RESET_ERROR);
        }
        // Eşzamanlı çift kullanım engeli: atomik UPDATE ... WHERE used_at IS NULL. 0 satır etkilenmişse
        // (başka bir istek aynı token'ı az önce "kazanmış") ikinci istek genel bir hata alır.
        int claimed = passwordResetTokenRepository.claim(tokenHash, Instant.now());
        if (claimed == 0) {
            throw new UnauthorizedException(GENERIC_RESET_ERROR);
        }
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        tokenRevocationService.revokeAllRefreshTokensForUser(user.getId());
        tokenRevocationService.invalidateAllUnusedPasswordResetTokensForUser(user.getId());
    }
}
