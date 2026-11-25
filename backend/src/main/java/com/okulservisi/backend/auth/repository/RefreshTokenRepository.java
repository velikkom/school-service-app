package com.okulservisi.backend.auth.repository;
import com.okulservisi.backend.auth.entity.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now where rt.user.id = :userId and rt.revokedAt is null")
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now where rt.tokenHash = :tokenHash and rt.revokedAt is null")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
