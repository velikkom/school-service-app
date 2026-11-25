package com.okulservisi.backend.auth.repository;
import com.okulservisi.backend.auth.entity.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int invalidateAllUnusedForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Eşzamanlı çift kullanımı DB seviyesinde engelleyen atomik "claim" güncellemesi:
     * sadece {@code used_at IS NULL} olan satır güncellenir. Dönen değer 1 ise bu çağrı
     * token'ı "kazanmıştır" (ilk istek), 0 ise token zaten kullanılmış/geçersizdir.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.tokenHash = :tokenHash and t.usedAt is null")
    int claim(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
