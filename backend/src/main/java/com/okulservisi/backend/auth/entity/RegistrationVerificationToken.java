package com.okulservisi.backend.auth.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code registration_verification_token} tablosu. {@link com.okulservisi.backend.auth.PasswordResetToken}
 * ile BİREBİR aynı desen (SHA-256 hash, tek kullanımlık {@code usedAt}, süreli {@code expiresAt}) —
 * sadece TTL farklı (24 saat, bkz. docs/DATABASE_DESIGN.md).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "registration_verification_token")
public class RegistrationVerificationToken extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_request_id", nullable = false)
    private RegistrationRequest registrationRequest;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
