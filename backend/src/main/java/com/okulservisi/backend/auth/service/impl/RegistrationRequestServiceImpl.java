package com.okulservisi.backend.auth.service.impl;

import com.okulservisi.backend.auth.entity.OAuthIdentity;
import com.okulservisi.backend.auth.repository.OAuthIdentityRepository;
import com.okulservisi.backend.auth.entity.RegistrationRequest;
import com.okulservisi.backend.auth.repository.RegistrationRequestRepository;
import com.okulservisi.backend.auth.entity.RegistrationRequestStatus;
import com.okulservisi.backend.auth.entity.RegistrationVerificationToken;
import com.okulservisi.backend.auth.repository.RegistrationVerificationTokenRepository;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestApproveRequest;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestCreateRequest;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestRejectRequest;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.auth.service.RateLimiterService;
import com.okulservisi.backend.auth.service.RegistrationRequestService;
import com.okulservisi.backend.auth.service.RegistrationVerificationIssuer;
import com.okulservisi.backend.auth.security.OpaqueTokenGenerator;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.common.exception.TooManyRequestsException;
import com.okulservisi.backend.common.exception.UnauthorizedException;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.repository.HostessRepository;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public kayıt talebi + ADMIN onay/red akışı (bkz. docs/SECURITY.md "Kayıt talebi ve ADMIN
 * onayı", docs/API.md §1 ve §18). {@link PasswordManagementService}/{@link AuthService} ile AYNI
 * token-claim deseni, ayrı bir sorumluluk (kayıt talebi yaşam döngüsü) olduğu için ayrı sınıf.
 */
@Service
@RequiredArgsConstructor
public class RegistrationRequestServiceImpl implements RegistrationRequestService {

    private static final String GENERIC_VERIFY_ERROR = "Token geçersiz veya süresi dolmuş.";
    /** Yeni satır AÇILMAZ, mevcut satır sıfırlanır (bkz. docs/SECURITY.md) kuralının geçerli olduğu durumlar. */
    private static final List<RegistrationRequestStatus> RESETTABLE_STATUSES =
            List.of(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION, RegistrationRequestStatus.REJECTED);

    private final RegistrationRequestRepository registrationRequestRepository;
    private final RegistrationVerificationTokenRepository registrationVerificationTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final RateLimiterService rateLimiterService;
    private final RegistrationVerificationIssuer registrationVerificationIssuer;
    private final OAuthIdentityRepository oAuthIdentityRepository;
    private final ParentRepository parentRepository;
    private final DriverRepository driverRepository;
    private final HostessRepository hostessRepository;

    /**
     * docs/SECURITY.md: kullanıcı var/yok fark etmeksizin her zaman 202 — bu metot (rate limit
     * dışında) asla istisna fırlatmaz.
     */
    @Transactional
    @Override
    public void createRequest(RegistrationRequestCreateRequest request, String clientIp) {
        if (!rateLimiterService.tryConsume(clientIp, request.email())) {
            throw new TooManyRequestsException("Çok fazla deneme yapıldı, lütfen daha sonra tekrar deneyin.");
        }
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BadRequestException("Şifreler eşleşmiyor.");
        }
        if (userRepository.findByEmailIgnoreCase(request.email()).filter(User::isActive).isPresent()) {
            // E-posta zaten AKTİF bir app_user'a ait - mevcut hesaba dokunulmaz, yeni talep açılmaz.
            return;
        }
        List<RegistrationRequest> resettable = registrationRequestRepository
                .findByEmailIgnoreCaseAndStatusIn(request.email(), RESETTABLE_STATUSES);
        RegistrationRequest registrationRequest = resettable.isEmpty() ? new RegistrationRequest() : resettable.get(0);
        boolean isReset = registrationRequest.getId() != null;

        registrationRequest.setFirstName(request.firstName());
        registrationRequest.setLastName(request.lastName());
        registrationRequest.setEmail(request.email().trim());
        registrationRequest.setPasswordHash(passwordEncoder.encode(request.password()));
        registrationRequest.setStatus(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION);
        registrationRequest.setEmailVerifiedAt(null);
        registrationRequest.setApprovedAt(null);
        registrationRequest.setApprovedByUser(null);
        registrationRequest.setRejectedAt(null);
        registrationRequest.setRejectionReason(null);
        registrationRequest.setCreatedUser(null);
        RegistrationRequest saved = registrationRequestRepository.save(registrationRequest);

        if (isReset) {
            registrationVerificationTokenRepository.invalidateAllUnusedForRequest(saved.getId(), Instant.now());
        }
        registrationVerificationIssuer.issueAndSend(saved);
    }

    @Transactional
    @Override
    public void verifyEmail(String token, String clientIp) {
        if (!rateLimiterService.tryConsume(clientIp, "verify-email:" + token.hashCode())) {
            throw new TooManyRequestsException("Çok fazla deneme yapıldı, lütfen daha sonra tekrar deneyin.");
        }
        String tokenHash = opaqueTokenGenerator.hash(token);
        RegistrationVerificationToken verificationToken = registrationVerificationTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException(GENERIC_VERIFY_ERROR));
        if (verificationToken.getExpiresAt().isBefore(Instant.now()) || verificationToken.getUsedAt() != null) {
            throw new UnauthorizedException(GENERIC_VERIFY_ERROR);
        }
        // Eşzamanlı çift kullanım engeli: atomik UPDATE ... WHERE used_at IS NULL (password_reset_token ile AYNI desen).
        int claimed = registrationVerificationTokenRepository.claim(tokenHash, Instant.now());
        if (claimed == 0) {
            throw new UnauthorizedException(GENERIC_VERIFY_ERROR);
        }
        RegistrationRequest registrationRequest = verificationToken.getRegistrationRequest();
        // Talep bu token issue edildikten SONRA reddedilmiş/onaylanmış olabilir (eski token hâlâ
        // "unused" + süresi dolmamış olabilir çünkü reject() token'ları invalidate ETMİYORDU - bkz.
        // reject() içindeki invalidateAllUnusedForRequest çağrısı). Sadece hâlâ
        // PENDING_EMAIL_VERIFICATION durumundaysa ilerlenir - aksi halde REDDEDİLMİŞ/ONAYLANMIŞ bir
        // talep sessizce EMAIL_VERIFIED'a geri alınıp ADMIN'in kararı atlatılmış olurdu.
        if (registrationRequest.getStatus() != RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION) {
            throw new UnauthorizedException(GENERIC_VERIFY_ERROR);
        }
        registrationRequest.setStatus(RegistrationRequestStatus.EMAIL_VERIFIED);
        registrationRequest.setEmailVerifiedAt(Instant.now());
        registrationRequestRepository.save(registrationRequest);
    }

    @Override
    public Page<RegistrationRequest> list(RegistrationRequestStatus status, Pageable pageable) {
        if (status != null) {
            return registrationRequestRepository.findByStatus(status, pageable);
        }
        // docs/API.md §18: status verilmezse tümü döner.
        return registrationRequestRepository.findAll(pageable);
    }

    @Override
    public RegistrationRequest getById(Long id) {
        return registrationRequestRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Kayıt talebi", id));
    }

    @Transactional
    @Override
    public User approve(Long id, RegistrationRequestApproveRequest request, AuthenticatedUser admin) {
        RegistrationRequest registrationRequest = getById(id);
        if (registrationRequest.getStatus() != RegistrationRequestStatus.EMAIL_VERIFIED) {
            throw new ConflictException(
                    "Bu talep ancak e-posta doğrulaması tamamlandıktan sonra onaylanabilir.");
        }
        // Talep oluşturulduktan sonra ADMIN aynı e-postayla manuel kullanıcı oluşturmuş olabilir - burada da kontrol edilir.
        if (userRepository.existsByEmailIgnoreCase(registrationRequest.getEmail())) {
            throw new ConflictException("Bu e-posta ile zaten bir kullanıcı var.");
        }
        // driver.license_no NOT NULL olduğu için Driver satırı licenseNo olmadan güvenle
        // oluşturulamaz - hiçbir satır yazılmadan ÖNCE kontrol edilir (bkz. docs/KNOWN_LIMITATIONS.md).
        if (request.role() == Role.DRIVER && (request.licenseNo() == null || request.licenseNo().isBlank())) {
            throw new BadRequestException("DRIVER rolü için ehliyet numarası (licenseNo) zorunludur.");
        }
        User adminUser = userRepository.findById(admin.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı."));

        User user = new User();
        user.setFullName(registrationRequest.getFirstName() + " " + registrationRequest.getLastName());
        user.setEmail(registrationRequest.getEmail());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setActive(true);
        user.setPasswordHash(registrationRequest.getPasswordHash());
        User savedUser = userRepository.save(user);

        // Role'e özgü domain profili burada oluşturulur — ADMIN'in eski iki adımlı akışında
        // (POST /api/users + POST /api/{parents|drivers|hostesses}) ikinci adımı kimse yapmadığı
        // için bu satır olmadan onaylanan kullanıcılar GERÇEKTE domain tablosuna sahip olmuyordu
        // (2026-10-25'te PARENT için ilk kez fark edildi — ParentService.getByUserId 404 atıyordu).
        switch (request.role()) {
            case PARENT -> {
                Parent parent = new Parent();
                parent.setUser(savedUser);
                parent.setFullName(savedUser.getFullName());
                parent.setPhone(request.phone());
                parentRepository.save(parent);
            }
            case DRIVER -> {
                Driver driver = new Driver();
                driver.setUser(savedUser);
                driver.setLicenseNo(request.licenseNo());
                driver.setActive(true);
                driverRepository.save(driver);
            }
            case HOSTESS -> {
                Hostess hostess = new Hostess();
                hostess.setUser(savedUser);
                hostess.setActive(true);
                hostessRepository.save(hostess);
            }
            default -> { /* ADMIN/STUDENT: ayrı bir domain profil tablosu yok. */ }
        }

        registrationRequest.setStatus(RegistrationRequestStatus.APPROVED);
        registrationRequest.setApprovedAt(Instant.now());
        registrationRequest.setApprovedByUser(adminUser);
        registrationRequest.setCreatedUser(savedUser);
        registrationRequestRepository.save(registrationRequest);

        // Bu talep sosyal girişten geldiyse (bkz. SocialAuthService#socialLogin), ona bağlı
        // oauth_identity satırları yeni oluşturulan app_user'a TAŞINIR; aksi halde onay sonrası
        // aynı sosyal kimlikle tekrar giriş denendiğinde hâlâ "pending" dalına düşerdi. XOR CHECK
        // kısıtına uymak için registrationRequest alanı null'a çekilir (bkz. docs/DATABASE_DESIGN.md
        // "oauth_identity").
        List<OAuthIdentity> socialIdentities =
                oAuthIdentityRepository.findByRegistrationRequestId(registrationRequest.getId());
        for (OAuthIdentity identity : socialIdentities) {
            identity.setUser(savedUser);
            identity.setRegistrationRequest(null);
        }
        oAuthIdentityRepository.saveAll(socialIdentities);

        return savedUser;
    }

    @Transactional
    @Override
    public RegistrationRequest reject(Long id, RegistrationRequestRejectRequest request, AuthenticatedUser admin) {
        RegistrationRequest registrationRequest = getById(id);
        if (registrationRequest.getStatus() != RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION
                && registrationRequest.getStatus() != RegistrationRequestStatus.EMAIL_VERIFIED) {
            throw new ConflictException("Bu talep zaten sonuçlandırılmış (onaylanmış veya reddedilmiş).");
        }
        registrationRequest.setStatus(RegistrationRequestStatus.REJECTED);
        registrationRequest.setRejectedAt(Instant.now());
        registrationRequest.setRejectionReason(request.reason());
        RegistrationRequest saved = registrationRequestRepository.save(registrationRequest);
        // Reddedilmeden ÖNCE issue edilmiş, henüz kullanılmamış doğrulama token'ı varsa iptal edilir
        // - aksi halde kullanıcı eski e-postasındaki linke tıklayarak reddedilen talebi
        // verifyEmail() üzerinden sessizce EMAIL_VERIFIED'a geri döndürebilirdi.
        registrationVerificationTokenRepository.invalidateAllUnusedForRequest(id, Instant.now());
        return saved;
    }
}
