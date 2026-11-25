package com.okulservisi.backend.auth.service.impl;

import com.okulservisi.backend.auth.entity.OAuthIdentity;
import com.okulservisi.backend.auth.repository.OAuthIdentityRepository;
import com.okulservisi.backend.auth.entity.RegistrationRequest;
import com.okulservisi.backend.auth.repository.RegistrationRequestRepository;
import com.okulservisi.backend.auth.entity.RegistrationRequestStatus;
import com.okulservisi.backend.auth.dto.response.LoginResponse;
import com.okulservisi.backend.auth.dto.request.SocialLinkRequest;
import com.okulservisi.backend.auth.dto.request.SocialLoginRequest;
import com.okulservisi.backend.auth.dto.response.SocialLoginResponse;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.auth.service.AuthService;
import com.okulservisi.backend.auth.service.RegistrationVerificationIssuer;
import com.okulservisi.backend.auth.service.SocialAuthService;
import com.okulservisi.backend.auth.security.OpaqueTokenGenerator;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.UnauthorizedException;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sosyal giriş (Google/Facebook/X) backend tarafı — docs/SECURITY.md "Sosyal giriş" bölümündeki
 * 3 senaryonun TAM uygulaması. OAuth2/PKCE protokol kodu BFF'tedir, bu servis SADECE BFF'ten
 * (server-to-server) ZATEN doğrulanmış kimlik bilgisini işler.
 *
 * {@code socialLogin} {@code /api/auth/social-login} SecurityConfig'te {@code permitAll} olduğu
 * için (tarayıcıdan gelen istekleri reddedemeyiz, sadece BFF'ten gelenleri kabul etmeliyiz) ÇAĞIRAN
 * TARAFIN gerçekten BFF olduğunu ayrıca doğrular ({@link #requireInternalCaller}) — aksi halde
 * herkes `emailVerified=true` gönderip e-posta doğrulamasını atlayabilir, hatta zaten bağlı bir
 * sosyal kimliğin provider+providerUserId çiftini tahmin ederek o kullanıcı olarak giriş yapabilirdi.
 */
@Service
@RequiredArgsConstructor
public class SocialAuthServiceImpl implements SocialAuthService {

    private final OAuthIdentityRepository oAuthIdentityRepository;
    private final UserRepository userRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final RegistrationVerificationIssuer registrationVerificationIssuer;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.internal-service-key:}")
    private String internalServiceKey;

    /**
     * Fail-closed: {@code internalServiceKey} boşsa (yapılandırılmamışsa) HER ZAMAN reddeder —
     * "boş header boş secret'a eşit" şeklinde bir bypass asla mümkün olmamalı.
     */
    private void requireInternalCaller(String providedKey) {
        if (internalServiceKey == null || internalServiceKey.isBlank()
                || providedKey == null || providedKey.isBlank()
                || !MessageDigest.isEqual(
                        internalServiceKey.getBytes(StandardCharsets.UTF_8),
                        providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Bu uç nokta doğrudan çağrılamaz.");
        }
    }

    @Transactional
    @Override
    public SocialLoginResponse socialLogin(SocialLoginRequest request, String internalCallerKey) {
        requireInternalCaller(internalCallerKey);
        Optional<OAuthIdentity> existing =
                oAuthIdentityRepository.findByProviderAndProviderUserId(request.provider(), request.providerUserId());

        if (existing.isPresent() && existing.get().getUser() != null) {
            User user = existing.get().getUser();
            if (!user.isActive()) {
                throw new UnauthorizedException("E-posta veya şifre hatalı.");
            }
            LoginResponse login = authService.issueTokensForUser(user);
            return SocialLoginResponse.authenticated(login);
        }

        if (existing.isPresent() && existing.get().getRegistrationRequest() != null) {
            // Bu kimlik zaten bir kayıt talebine bağlı (örn. kullanıcı sosyal giriş butonuna
            // tekrar tıkladı) - tarayıcıya token verilmez, mevcut talebin durumu bildirilir.
            return SocialLoginResponse.pendingApproval("Kayıt talebiniz zaten alındı, ADMIN onayı bekleniyor.");
        }

        Optional<User> activeUserWithSameEmail = userRepository.findByEmailIgnoreCase(request.email())
                .filter(User::isActive);
        if (activeUserWithSameEmail.isPresent()) {
            // Aynı e-postanın dönmesi otomatik bağlanma için yeterli KANIT sayılmaz.
            return SocialLoginResponse.needsLinking(
                    "Bu e-postayla zaten bir hesabınız var, bağlamak için mevcut şifrenizle giriş yapın.");
        }

        RegistrationRequest registrationRequest = createRegistrationRequestFromSocial(request);
        OAuthIdentity identity = new OAuthIdentity();
        identity.setProvider(request.provider());
        identity.setProviderUserId(request.providerUserId());
        identity.setEmail(request.email());
        identity.setRegistrationRequest(registrationRequest);
        oAuthIdentityRepository.save(identity);

        return SocialLoginResponse.pendingApproval("Kayıt talebiniz alındı, ADMIN onayı bekleniyor.");
    }

    @Transactional
    @Override
    public void socialLink(AuthenticatedUser principal, SocialLinkRequest request) {
        Optional<OAuthIdentity> existing =
                oAuthIdentityRepository.findByProviderAndProviderUserId(request.provider(), request.providerUserId());
        if (existing.isPresent()) {
            OAuthIdentity identity = existing.get();
            boolean alreadyLinkedToPrincipal = identity.getUser() != null
                    && identity.getUser().getId().equals(principal.getUserId());
            if (alreadyLinkedToPrincipal) {
                return; // idempotent
            }
            throw new ConflictException("Bu sosyal hesap başka bir kullanıcıya bağlı.");
        }
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı."));
        OAuthIdentity identity = new OAuthIdentity();
        identity.setProvider(request.provider());
        identity.setProviderUserId(request.providerUserId());
        identity.setEmail(request.email());
        identity.setUser(user);
        oAuthIdentityRepository.save(identity);
    }

    /**
     * Sağlayıcı {@code emailVerified=true} dediyse ADMIN direkt onaylayabilsin diye {@code
     * EMAIL_VERIFIED} ile açılır (kendi doğrulama mailimiz GÖNDERİLMEZ - sağlayıcı zaten
     * doğrulamış); değilse {@code PENDING_EMAIL_VERIFICATION} + kendi doğrulama token'ımız/mailimiz.
     */
    private RegistrationRequest createRegistrationRequestFromSocial(SocialLoginRequest request) {
        String[] names = splitFullName(request.fullName());
        RegistrationRequest registrationRequest = new RegistrationRequest();
        registrationRequest.setFirstName(names[0]);
        registrationRequest.setLastName(names[1]);
        registrationRequest.setEmail(request.email().trim());
        // Sosyal girişte kullanıcının kendi seçtiği bir şifre YOK - rastgele, kullanılamaz bir
        // placeholder hash üretilir (bu kullanıcı normal login akışıyla giriş yapmaz, ADMIN onayı
        // sonrası dahi sadece sosyal girişle devam eder; ileride şifre belirlemek isterse
        // forgot-password akışını kullanabilir).
        registrationRequest.setPasswordHash(passwordEncoder.encode(opaqueTokenGenerator.generate()));

        if (request.emailVerified()) {
            registrationRequest.setStatus(RegistrationRequestStatus.EMAIL_VERIFIED);
            registrationRequest.setEmailVerifiedAt(Instant.now());
            RegistrationRequest saved = registrationRequestRepository.save(registrationRequest);
            return saved;
        }

        registrationRequest.setStatus(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION);
        RegistrationRequest saved = registrationRequestRepository.save(registrationRequest);
        registrationVerificationIssuer.issueAndSend(saved);
        return saved;
    }

    private String[] splitFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[] {"Sosyal", "Kullanıcı"};
        }
        String trimmed = fullName.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex < 0) {
            return new String[] {trimmed, ""};
        }
        return new String[] {trimmed.substring(0, spaceIndex), trimmed.substring(spaceIndex + 1).trim()};
    }
}
