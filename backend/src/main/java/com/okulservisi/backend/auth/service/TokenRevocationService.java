package com.okulservisi.backend.auth.service;

import com.okulservisi.backend.auth.repository.PasswordResetTokenRepository;
import com.okulservisi.backend.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kullanıcının tüm oturumlarını/token'larını iptal eden paylaşılan servis. Hem {@code auth}
 * (şifre/reset akışları) hem de {@code user} (rol/isActive değişikliği) modülü tarafından
 * kullanılır — docs/RBAC_MATRIX.md kural 4: "Rol veya parola değişikliğinde kullanıcının tüm
 * aktif oturumları/refresh token'ları geçersizleştirilir".
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Transactional
    public void revokeAllRefreshTokensForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }

    @Transactional
    public void invalidateAllUnusedPasswordResetTokensForUser(Long userId) {
        passwordResetTokenRepository.invalidateAllUnusedForUser(userId, Instant.now());
    }
}
