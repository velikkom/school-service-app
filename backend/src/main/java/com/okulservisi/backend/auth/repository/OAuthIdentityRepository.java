package com.okulservisi.backend.auth.repository;
import com.okulservisi.backend.auth.entity.OAuthIdentity;
import com.okulservisi.backend.auth.entity.OAuthProvider;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {

    Optional<OAuthIdentity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    Optional<OAuthIdentity> findByUserId(Long userId);

    /**
     * Sosyal girişten açılmış bir registration_request'e bağlı TÜM oauth_identity satırları
     * (RegistrationRequestService#approve bu satırları yeni oluşturulan app_user'a taşımak için
     * kullanır - bkz. docs/DATABASE_DESIGN.md "oauth_identity").
     */
    List<OAuthIdentity> findByRegistrationRequestId(Long registrationRequestId);
}
