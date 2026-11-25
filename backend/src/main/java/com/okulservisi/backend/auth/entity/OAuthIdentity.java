package com.okulservisi.backend.auth.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import com.okulservisi.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code oauth_identity} tablosu. {@code user}/{@code registrationRequest}'ten TAM OLARAK BİRİ
 * dolu olmalı — bu kısıt hem DB'de (CHECK, bkz. V11 migration) hem serviste
 * ({@link com.okulservisi.backend.auth.service.SocialAuthService}) uygulanır (bkz.
 * docs/SECURITY.md "Sosyal giriş").
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "oauth_identity")
public class OAuthIdentity extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "email")
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_request_id")
    private RegistrationRequest registrationRequest;
}
