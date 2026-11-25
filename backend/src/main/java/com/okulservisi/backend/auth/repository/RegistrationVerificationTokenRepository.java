package com.okulservisi.backend.auth.repository;
import com.okulservisi.backend.auth.entity.RegistrationVerificationToken;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@link PasswordResetTokenRepository} ile BİREBİR aynı desen. */
public interface RegistrationVerificationTokenRepository extends JpaRepository<RegistrationVerificationToken, Long> {

    Optional<RegistrationVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RegistrationVerificationToken t set t.usedAt = :now "
            + "where t.registrationRequest.id = :registrationRequestId and t.usedAt is null")
    int invalidateAllUnusedForRequest(@Param("registrationRequestId") Long registrationRequestId, @Param("now") Instant now);

    /**
     * Eşzamanlı çift kullanımı DB seviyesinde engelleyen atomik "claim" güncellemesi —
     * {@link PasswordResetTokenRepository#claim} ile AYNI desen.
     */
    @Modifying
    @Query("update RegistrationVerificationToken t set t.usedAt = :now where t.tokenHash = :tokenHash and t.usedAt is null")
    int claim(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
