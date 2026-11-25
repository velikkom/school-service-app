package com.okulservisi.backend.auth;
import com.okulservisi.backend.auth.entity.OAuthIdentity;
import com.okulservisi.backend.auth.entity.OAuthProvider;
import com.okulservisi.backend.auth.entity.RegistrationRequestStatus;
import com.okulservisi.backend.auth.entity.RegistrationVerificationToken;
import com.okulservisi.backend.auth.entity.RegistrationRequest;
import com.okulservisi.backend.auth.repository.OAuthIdentityRepository;
import com.okulservisi.backend.auth.repository.RegistrationRequestRepository;
import com.okulservisi.backend.auth.repository.RegistrationVerificationTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.dto.request.LoginRequest;
import com.okulservisi.backend.auth.dto.response.LoginResponse;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestApproveRequest;
import com.okulservisi.backend.auth.dto.response.RegistrationRequestApproveResponse;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestCreateRequest;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestRejectRequest;
import com.okulservisi.backend.auth.dto.response.RegistrationRequestResponse;
import com.okulservisi.backend.auth.dto.request.SocialLoginRequest;
import com.okulservisi.backend.auth.dto.response.SocialLoginResponse;
import com.okulservisi.backend.auth.dto.response.SocialLoginStatus;
import com.okulservisi.backend.auth.dto.request.VerifyEmailRequest;
import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.auth.security.OpaqueTokenGenerator;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.repository.HostessRepository;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/SECURITY.md "Kayıt talebi ve ADMIN onayı" + docs/API.md §1/§18 + docs/RBAC_MATRIX.md
 * kuralı 1'in SINIRLI güncellemesini doğrulayan gerçek, izole Testcontainers Postgres entegrasyon
 * testleri.
 */
class RegistrationRequestControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RegistrationRequestRepository registrationRequestRepository;

    @Autowired
    private RegistrationVerificationTokenRepository registrationVerificationTokenRepository;

    @Autowired
    private OpaqueTokenGenerator opaqueTokenGenerator;

    @Autowired
    private OAuthIdentityRepository oAuthIdentityRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private HostessRepository hostessRepository;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private String uniqueEmail() {
        return "reg-" + UUID.randomUUID() + "@ssp-test.local";
    }

    private String uniqueProviderUserId() {
        return "google-uid-" + UUID.randomUUID();
    }

    // application-test.yml "app.security.internal-service-key" ile AYNI olmalı (bkz. SocialAuthControllerTest).
    private ResponseEntity<SocialLoginResponse> postSocialLogin(SocialLoginRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service-Key", "test-only-internal-service-key-32bytes");
        return restTemplate().exchange("/api/auth/social-login", HttpMethod.POST,
                new HttpEntity<>(request, headers), SocialLoginResponse.class);
    }

    /** PasswordResetConcurrencyTest ile AYNI gerekçe: ham token normalde sadece e-postaya yazılır. */
    private String createActiveVerificationToken(RegistrationRequest registrationRequest, Instant expiresAt) {
        String rawToken = opaqueTokenGenerator.generate();
        RegistrationVerificationToken token = new RegistrationVerificationToken();
        token.setRegistrationRequest(registrationRequest);
        token.setTokenHash(opaqueTokenGenerator.hash(rawToken));
        token.setExpiresAt(expiresAt);
        registrationVerificationTokenRepository.save(token);
        return rawToken;
    }

    private RegistrationRequest findSingleByEmail(String email) {
        List<RegistrationRequest> matches = registrationRequestRepository.findByEmailIgnoreCaseAndStatusIn(
                email, List.of(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION,
                        RegistrationRequestStatus.EMAIL_VERIFIED, RegistrationRequestStatus.APPROVED,
                        RegistrationRequestStatus.REJECTED));
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    // ---- Happy path: talep -> (manuel) doğrulama -> ADMIN onayı -> yeni kullanıcı GERÇEKTEN login olabiliyor ----
    @Test
    void fullFlow_createVerifyApprove_newUserCanLoginWithChosenPassword() {
        String email = uniqueEmail();
        RegistrationRequestCreateRequest createRequest =
                new RegistrationRequestCreateRequest("Ayşe", "Yılmaz", email, "Chosen-Pass123!", "Chosen-Pass123!");

        ResponseEntity<Void> createResponse = restTemplate().postForEntity(
                "/api/auth/register-requests", createRequest, Void.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        RegistrationRequest persisted = findSingleByEmail(email);
        assertThat(persisted.getStatus()).isEqualTo(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION);

        // E-posta doğrulamasını gerçek token akışıyla tamamla (ayrı testlerde token claim/expiry
        // ayrıntılı doğrulanıyor - burada sadece ADMIN onayının önkoşulu sağlanıyor).
        String rawToken = createActiveVerificationToken(persisted, Instant.now().plus(24, ChronoUnit.HOURS));
        ResponseEntity<Void> verifyResponse = restTemplate().postForEntity(
                "/api/auth/register-requests/verify-email", new VerifyEmailRequest(rawToken), Void.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        RegistrationRequest afterVerify = registrationRequestRepository.findById(persisted.getId()).orElseThrow();
        assertThat(afterVerify.getStatus()).isEqualTo(RegistrationRequestStatus.EMAIL_VERIFIED);
        assertThat(afterVerify.getEmailVerifiedAt()).isNotNull();

        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        ResponseEntity<RegistrationRequestApproveResponse> approveResponse = restTemplate().exchange(
                "/api/registration-requests/" + persisted.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestApproveRequest(Role.PARENT, "5551234567", null), authHeaderFor(admin)),
                RegistrationRequestApproveResponse.class);

        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        RegistrationRequestApproveResponse approveBody = approveResponse.getBody();
        assertThat(approveBody).isNotNull();
        assertThat(approveBody.user().email()).isEqualToIgnoringCase(email);
        assertThat(approveBody.user().role()).isEqualTo(Role.PARENT);
        assertThat(approveBody.user().isActive()).isTrue();

        RegistrationRequest afterApprove = registrationRequestRepository.findById(persisted.getId()).orElseThrow();
        assertThat(afterApprove.getStatus()).isEqualTo(RegistrationRequestStatus.APPROVED);
        assertThat(afterApprove.getApprovedAt()).isNotNull();
        assertThat(afterApprove.getCreatedUser()).isNotNull();
        assertThat(afterApprove.getApprovedByUser().getId()).isEqualTo(admin.getId());

        // Uçtan uca doğrulama: yeni kullanıcı GERÇEKTEN kendi seçtiği şifreyle login olabiliyor.
        ResponseEntity<LoginResponse> loginResponse = restTemplate().postForEntity(
                "/api/auth/login", new LoginRequest(email, "Chosen-Pass123!", null), LoginResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        assertThat(loginResponse.getBody().user().role()).isEqualTo(Role.PARENT);

        // 2026-10-25 regresyonu: PARENT onaylandığında bağlı bir `parent` satırı da oluşmalı —
        // önceden sadece `app_user` oluşturuluyordu, ParentService.getByUserId() bu kullanıcılar
        // için her zaman 404 atıyordu (veli öz-kayıt sihirbazı bunu ilk kez açığa çıkardı).
        User createdUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        Parent createdParent = parentRepository.findByUserId(createdUser.getId()).orElseThrow(
                () -> new AssertionError("PARENT onaylandıktan sonra bir Parent satırı oluşmalıydı."));
        assertThat(createdParent.getFullName()).isEqualTo("Ayşe Yılmaz");
        assertThat(createdParent.getPhone()).isEqualTo("5551234567");
    }

    // ---- 2026-11-04: HOSTESS onaylandığında bağlı bir `hostess` satırı da oluşmalı (hostess'in
    // PARENT dışında hiçbir zorunlu ek alanı yok, bu yüzden PARENT'la AYNI turda düzeltildi). ----
    @Test
    void approve_withRoleHostess_createsHostessRow() {
        RegistrationRequest registrationRequest = newEmailVerifiedRequest(uniqueEmail());
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<RegistrationRequestApproveResponse> response = restTemplate().exchange(
                "/api/registration-requests/" + registrationRequest.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestApproveRequest(Role.HOSTESS, null, null), authHeaderFor(admin)),
                RegistrationRequestApproveResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long newUserId = response.getBody().user().id();
        Hostess createdHostess = hostessRepository.findByUserId(newUserId).orElseThrow(
                () -> new AssertionError("HOSTESS onaylandıktan sonra bir Hostess satırı oluşmalıydı."));
        assertThat(createdHostess.isActive()).isTrue();
    }

    // ---- 2026-11-04: DRIVER onayı licenseNo gerektirir (driver.license_no NOT NULL), verilirse
    // bağlı bir `driver` satırı oluşur; verilmezse hiçbir satır yazılmadan 400 döner. ----
    @Test
    void approve_withRoleDriverAndLicenseNo_createsDriverRow() {
        RegistrationRequest registrationRequest = newEmailVerifiedRequest(uniqueEmail());
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<RegistrationRequestApproveResponse> response = restTemplate().exchange(
                "/api/registration-requests/" + registrationRequest.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestApproveRequest(Role.DRIVER, null, "B-123456"), authHeaderFor(admin)),
                RegistrationRequestApproveResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long newUserId = response.getBody().user().id();
        Driver createdDriver = driverRepository.findByUserId(newUserId).orElseThrow(
                () -> new AssertionError("DRIVER onaylandıktan sonra bir Driver satırı oluşmalıydı."));
        assertThat(createdDriver.getLicenseNo()).isEqualTo("B-123456");
        assertThat(createdDriver.isActive()).isTrue();
    }

    @Test
    void approve_withRoleDriverAndNoLicenseNo_returns400AndCreatesNoUser() {
        RegistrationRequest registrationRequest = newEmailVerifiedRequest(uniqueEmail());
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/registration-requests/" + registrationRequest.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestApproveRequest(Role.DRIVER, null, null), authHeaderFor(admin)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findByEmailIgnoreCase(registrationRequest.getEmail())).isEmpty();
        RegistrationRequest reloaded = registrationRequestRepository.findById(registrationRequest.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).as("başarısız onay talebi EMAIL_VERIFIED'ta kalmalı")
                .isEqualTo(RegistrationRequestStatus.EMAIL_VERIFIED);
    }

    // ---- ADMIN onayı sosyal girişten gelen talebi onayladığında, bağlı oauth_identity yeni
    // app_user'a TAŞINMALI - onay sonrası AYNI (provider, providerUserId) ile tekrar social-login
    // çağrıldığında artık AUTHENTICATED (gerçek token) dönmeli, PENDING_APPROVAL'a tekrar düşmemeli. ----
    @Test
    void approve_whenRequestCameFromSocialLogin_movesOAuthIdentityToNewUserAndEnablesSocialLogin() {
        String providerUserId = uniqueProviderUserId();
        String email = uniqueEmail();
        SocialLoginRequest socialLoginRequest =
                new SocialLoginRequest(OAuthProvider.GOOGLE, providerUserId, email, true, "Sosyal Kullanıcı");

        ResponseEntity<SocialLoginResponse> firstSocialLogin = postSocialLogin(socialLoginRequest);
        assertThat(firstSocialLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstSocialLogin.getBody()).isNotNull();
        assertThat(firstSocialLogin.getBody().status()).isEqualTo(SocialLoginStatus.PENDING_APPROVAL);

        RegistrationRequest registrationRequest = findSingleByEmail(email);
        assertThat(registrationRequest.getStatus()).isEqualTo(RegistrationRequestStatus.EMAIL_VERIFIED);
        OAuthIdentity identityBeforeApprove = oAuthIdentityRepository
                .findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId)
                .orElseThrow();
        assertThat(identityBeforeApprove.getUser()).isNull();
        assertThat(identityBeforeApprove.getRegistrationRequest()).isNotNull();

        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        ResponseEntity<RegistrationRequestApproveResponse> approveResponse = restTemplate().exchange(
                "/api/registration-requests/" + registrationRequest.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestApproveRequest(Role.PARENT, null, null), authHeaderFor(admin)),
                RegistrationRequestApproveResponse.class);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approveResponse.getBody()).isNotNull();
        Long newUserId = approveResponse.getBody().user().id();

        // oauth_identity artık yeni app_user'a bağlı, registration_request alanı null (XOR CHECK).
        OAuthIdentity identityAfterApprove = oAuthIdentityRepository
                .findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId)
                .orElseThrow();
        assertThat(identityAfterApprove.getRegistrationRequest()).isNull();
        assertThat(identityAfterApprove.getUser()).isNotNull();
        assertThat(identityAfterApprove.getUser().getId()).isEqualTo(newUserId);

        // Asıl regresyon: AYNI (provider, providerUserId) ile tekrar social-login artık
        // AUTHENTICATED dönmeli (eskiden hâlâ PENDING_APPROVAL'a düşerdi).
        ResponseEntity<SocialLoginResponse> secondSocialLogin = postSocialLogin(socialLoginRequest);
        assertThat(secondSocialLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        SocialLoginResponse secondBody = secondSocialLogin.getBody();
        assertThat(secondBody).isNotNull();
        assertThat(secondBody.status()).isEqualTo(SocialLoginStatus.AUTHENTICATED);
        assertThat(secondBody.accessToken()).isNotBlank();
        assertThat(secondBody.refreshToken()).isNotBlank();
        assertThat(secondBody.user()).isNotNull();
        assertThat(secondBody.user().id()).isEqualTo(newUserId);
    }

    // ---- docs/CURRENT_STATE_REPORT.md §7 "Not — KISMİ sayılan tek kontrol": RegistrationRequestCreateRequest
    // record'unda role/isActive/approved alanı BİLEREK YOK (bkz. DTO Javadoc'u), ama bu şu ana kadar
    // HİÇBİR testle kilitlenmemişti. Jackson'ın varsayılan FAIL_ON_UNKNOWN_PROPERTIES=false ayarı
    // nedeniyle body'de DTO'da olmayan "role"/"isActive"/"approved" alanları gönderilirse sessizce
    // yok sayılır - bu testin amacı TAM OLARAK bunu (üçünü BİRDEN) kilitlemek: public uçtan bu
    // alanları göndermek NE isteği reddettirir (400) NE DE talebi "zaten onaylanmış/aktif" bir
    // duruma sokar NE DE final kullanıcı rolünü etkiler; rolü SADECE ADMIN'in /approve adımındaki
    // seçimi belirler. ----
    @Test
    void createRequest_withUnknownPrivilegeFields_ignoredAndApprovalRoleStillDecidesFinalRole() {
        String email = uniqueEmail();

        // Ham Map kullanılıyor: hedef DTO'da olmayan "role"/"isActive"/"approved" alanları
        // TestRestTemplate'in Jackson serializer'ı tarafından olduğu gibi gövdeye yazılır, sunucu
        // tarafında ise RegistrationRequestCreateRequest'te karşılığı olmadığı için yoksayılmaları
        // beklenir.
        ResponseEntity<Void> createResponse = restTemplate().exchange(
                "/api/auth/register-requests", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "firstName", "Deneme",
                        "lastName", "Kullanıcı",
                        "email", email,
                        "password", "Some-Pass123!",
                        "passwordConfirm", "Some-Pass123!",
                        "role", "ADMIN",
                        "isActive", true,
                        "approved", true)),
                Void.class);

        // Regresyon riski: ekstra/bilinmeyen alan(lar) sunucuyu 400'e DÜŞÜRMEMELİ.
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        RegistrationRequest persisted = findSingleByEmail(email);
        assertThat(persisted.getStatus())
                .as("ekstra \"isActive\"/\"approved\" alanları talebi 'zaten onaylanmış' bir duruma SOKMAMALI")
                .isEqualTo(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(persisted.getApprovedAt()).as("ekstra \"approved\":true talebi GERÇEKTEN onaylamamalı").isNull();

        String rawToken = createActiveVerificationToken(persisted, Instant.now().plus(24, ChronoUnit.HOURS));
        ResponseEntity<Void> verifyResponse = restTemplate().postForEntity(
                "/api/auth/register-requests/verify-email", new VerifyEmailRequest(rawToken), Void.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        ResponseEntity<RegistrationRequestApproveResponse> approveResponse = restTemplate().exchange(
                "/api/registration-requests/" + persisted.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestApproveRequest(Role.PARENT, null, null), authHeaderFor(admin)),
                RegistrationRequestApproveResponse.class);

        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        RegistrationRequestApproveResponse approveBody = approveResponse.getBody();
        assertThat(approveBody).isNotNull();
        assertThat(approveBody.user().role())
                .as("public body'deki \"role\":\"ADMIN\" alanının HİÇBİR etkisi olmamalı - "
                        + "final rolü SADECE ADMIN'in approve adımında seçtiği Role.PARENT belirlemeli")
                .isEqualTo(Role.PARENT);

        // Ekstra güvence: kullanıcı satırını doğrudan DB'den çekip bir kez daha doğrula.
        User createdUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(createdUser.getRole()).isEqualTo(Role.PARENT);
    }

    // ---- "Onay öncesi erişim yok" (sprint talebinin ikinci yarısı — sadece role alanının yok
    // sayılması değil, ADMIN onayından ÖNCE talebin hiçbir şekilde kullanılabilir bir hesaba
    // dönüşmediğinin de kilitlenmesi istendi). Bir registration_request satırı var olması,
    // login/korumalı endpoint erişimi SAĞLAMAZ - çünkü approve() çağrılana kadar hiçbir app_user
    // satırı oluşmaz. ----
    @Test
    void createRequest_beforeAdminApproval_credentialsCannotLoginAtAnyStage() {
        String email = uniqueEmail();
        String password = "Gercek-Pass123!";

        restTemplate().postForEntity("/api/auth/register-requests",
                new RegistrationRequestCreateRequest("Onay", "Beklayan", email, password, password), Void.class);
        RegistrationRequest persisted = findSingleByEmail(email);

        // Aşama 1: e-posta HENÜZ doğrulanmadan - login denenirse başarısız olmalı (app_user yok).
        ResponseEntity<ApiError> loginBeforeVerify = restTemplate().postForEntity(
                "/api/auth/login", new LoginRequest(email, password, null), ApiError.class);
        assertThat(loginBeforeVerify.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Aşama 2: e-posta doğrulandı (EMAIL_VERIFIED) ama ADMIN HENÜZ onaylamadı - login HÂLÂ başarısız
        // olmalı; bu aşama tam olarak "onay öncesi erişim yok" iddiasının test edildiği noktadır.
        String rawToken = createActiveVerificationToken(persisted, Instant.now().plus(24, ChronoUnit.HOURS));
        restTemplate().postForEntity(
                "/api/auth/register-requests/verify-email", new VerifyEmailRequest(rawToken), Void.class);
        RegistrationRequest afterVerify = registrationRequestRepository.findById(persisted.getId()).orElseThrow();
        assertThat(afterVerify.getStatus()).isEqualTo(RegistrationRequestStatus.EMAIL_VERIFIED);

        ResponseEntity<ApiError> loginAfterVerifyBeforeApprove = restTemplate().postForEntity(
                "/api/auth/login", new LoginRequest(email, password, null), ApiError.class);
        assertThat(loginAfterVerifyBeforeApprove.getStatusCode())
                .as("EMAIL_VERIFIED olması TEK BAŞINA erişim vermemeli - ADMIN onayı zorunlu")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(userRepository.findByEmailIgnoreCase(email))
                .as("ADMIN onaylamadan hiçbir app_user satırı oluşmamalı").isEmpty();

        // Aşama 3: ADMIN onayladıktan SONRA artık login başarılı olmalı (negatif kontrolün pozitif
        // tarafı - yukarıdaki 401'lerin login akışının kendisi bozuk olduğu için DEĞİL, gerçekten
        // "henüz onaylanmadığı için" olduğunu kanıtlar).
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        restTemplate().exchange(
                "/api/registration-requests/" + persisted.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestApproveRequest(Role.PARENT, null, null), authHeaderFor(admin)),
                RegistrationRequestApproveResponse.class);

        ResponseEntity<LoginResponse> loginAfterApprove = restTemplate().postForEntity(
                "/api/auth/login", new LoginRequest(email, password, null), LoginResponse.class);
        assertThat(loginAfterApprove.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- "Her zaman 202" + var/yok ayrımı yapmama ----
    @Test
    void createRequest_withExistingActiveEmail_returns202ButCreatesNoRow() {
        User activeUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<Void> response = restTemplate().postForEntity(
                "/api/auth/register-requests",
                new RegistrationRequestCreateRequest("X", "Y", activeUser.getEmail(), "Whatever-Pass1!", "Whatever-Pass1!"),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        List<RegistrationRequest> matches = registrationRequestRepository.findByEmailIgnoreCaseAndStatusIn(
                activeUser.getEmail(), List.of(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION));
        assertThat(matches).isEmpty();
    }

    // ---- Tekrarlı talep aynı satırı sıfırlar, yeni satır açmaz ----
    @Test
    void createRequest_calledTwiceWithSameEmail_resetsSameRowInsteadOfCreatingNew() {
        String email = uniqueEmail();

        ResponseEntity<Void> first = restTemplate().postForEntity(
                "/api/auth/register-requests",
                new RegistrationRequestCreateRequest("Birinci", "Ad", email, "First-Pass123!", "First-Pass123!"),
                Void.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        RegistrationRequest afterFirst = findSingleByEmail(email);
        Long rowId = afterFirst.getId();

        ResponseEntity<Void> second = restTemplate().postForEntity(
                "/api/auth/register-requests",
                new RegistrationRequestCreateRequest("Ikinci", "Ad", email, "Second-Pass456!", "Second-Pass456!"),
                Void.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        RegistrationRequest afterSecond = findSingleByEmail(email);
        assertThat(afterSecond.getId()).as("yeni satır AÇILMAMALI, aynı satır sıfırlanmalı").isEqualTo(rowId);
        assertThat(afterSecond.getFirstName()).isEqualTo("Ikinci");
        assertThat(afterSecond.getStatus()).isEqualTo(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION);

        // Eski doğrulama token'ı artık kullanılabilir olmamalı (invalidateAllUnusedForRequest).
        List<RegistrationVerificationToken> tokens = registrationVerificationTokenRepository.findAll().stream()
                .filter(t -> t.getRegistrationRequest().getId().equals(rowId))
                .toList();
        assertThat(tokens).isNotEmpty();
        assertThat(tokens.stream().filter(t -> t.getUsedAt() == null)).hasSize(1);
    }

    // ---- verify-email: başarı/expired/already-used ----
    @Test
    void verifyEmail_withValidToken_setsStatusToEmailVerified() {
        RegistrationRequest registrationRequest = newPendingRequest(uniqueEmail());
        String rawToken = createActiveVerificationToken(registrationRequest, Instant.now().plus(24, ChronoUnit.HOURS));

        ResponseEntity<Void> response = restTemplate().postForEntity(
                "/api/auth/register-requests/verify-email", new VerifyEmailRequest(rawToken), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        RegistrationRequest reloaded = registrationRequestRepository.findById(registrationRequest.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RegistrationRequestStatus.EMAIL_VERIFIED);
    }

    @Test
    void verifyEmail_withExpiredToken_returns401AndStatusUnchanged() {
        RegistrationRequest registrationRequest = newPendingRequest(uniqueEmail());
        String rawToken = createActiveVerificationToken(registrationRequest, Instant.now().minus(1, ChronoUnit.MINUTES));

        ResponseEntity<ApiError> response = restTemplate().postForEntity(
                "/api/auth/register-requests/verify-email", new VerifyEmailRequest(rawToken), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        RegistrationRequest reloaded = registrationRequestRepository.findById(registrationRequest.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION);
    }

    @Test
    void verifyEmail_reusingSameToken_secondAttemptFails() {
        RegistrationRequest registrationRequest = newPendingRequest(uniqueEmail());
        String rawToken = createActiveVerificationToken(registrationRequest, Instant.now().plus(24, ChronoUnit.HOURS));

        ResponseEntity<Void> first = restTemplate().postForEntity(
                "/api/auth/register-requests/verify-email", new VerifyEmailRequest(rawToken), Void.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiError> second = restTemplate().postForEntity(
                "/api/auth/register-requests/verify-email", new VerifyEmailRequest(rawToken), ApiError.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- approve SADECE EMAIL_VERIFIED durumunda izinli ----
    @Test
    void approve_whenStatusIsPending_returns409AndCreatesNoUser() {
        RegistrationRequest registrationRequest = newPendingRequest(uniqueEmail());
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/registration-requests/" + registrationRequest.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestApproveRequest(Role.PARENT, null, null), authHeaderFor(admin)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findByEmailIgnoreCase(registrationRequest.getEmail())).isEmpty();
    }

    // ---- reject kullanıcı oluşturmuyor ----
    @Test
    void reject_whenEmailVerified_marksRejectedAndCreatesNoUser() {
        RegistrationRequest registrationRequest = newPendingRequest(uniqueEmail());
        registrationRequest.setStatus(RegistrationRequestStatus.EMAIL_VERIFIED);
        registrationRequest.setEmailVerifiedAt(Instant.now());
        registrationRequestRepository.save(registrationRequest);
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<RegistrationRequestResponse> response = restTemplate().exchange(
                "/api/registration-requests/" + registrationRequest.getId() + "/reject", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestRejectRequest("uygun değil"), authHeaderFor(admin)),
                RegistrationRequestResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RegistrationRequest reloaded = registrationRequestRepository.findById(registrationRequest.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RegistrationRequestStatus.REJECTED);
        assertThat(reloaded.getRejectionReason()).isEqualTo("uygun değil");
        assertThat(userRepository.findByEmailIgnoreCase(registrationRequest.getEmail())).isEmpty();
    }

    // ---- Güvenlik regresyonu: reddedilmeden ÖNCE issue edilmiş eski doğrulama linki, reddedilen
    // bir talebi sessizce EMAIL_VERIFIED'a GERİ DÖNDÜREMEMELİ (ADMIN'in kararını atlatmamalı). ----
    @Test
    void verifyEmail_withTokenIssuedBeforeRejection_doesNotResurrectRejectedRequest() {
        RegistrationRequest registrationRequest = newPendingRequest(uniqueEmail());
        String rawToken = createActiveVerificationToken(registrationRequest, Instant.now().plus(24, ChronoUnit.HOURS));
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<RegistrationRequestResponse> rejectResponse = restTemplate().exchange(
                "/api/registration-requests/" + registrationRequest.getId() + "/reject", HttpMethod.POST,
                new HttpEntity<>(new RegistrationRequestRejectRequest("uygun değil"), authHeaderFor(admin)),
                RegistrationRequestResponse.class);
        assertThat(rejectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Reddedilmeden ÖNCE gönderilmiş eski e-postadaki link hâlâ "kullanıcının elinde" olabilir.
        ResponseEntity<ApiError> verifyResponse = restTemplate().postForEntity(
                "/api/auth/register-requests/verify-email", new VerifyEmailRequest(rawToken), ApiError.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        RegistrationRequest reloaded = registrationRequestRepository.findById(registrationRequest.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).as("REJECTED durumu korunmalı, EMAIL_VERIFIED'a geri DÖNMEMELİ")
                .isEqualTo(RegistrationRequestStatus.REJECTED);
    }

    // ---- RBAC: ADMIN olmayan roller 403 ----
    @Test
    void listAndApprove_asNonAdmin_returns403() {
        RegistrationRequest registrationRequest = newPendingRequest(uniqueEmail());
        for (Role role : new Role[] {Role.DRIVER, Role.HOSTESS, Role.PARENT, Role.STUDENT}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);

            ResponseEntity<ApiError> listResponse = restTemplate().exchange(
                    "/api/registration-requests", HttpMethod.GET, new HttpEntity<>(authHeaderFor(user)), ApiError.class);
            assertThat(listResponse.getStatusCode()).as("role=%s list", role).isEqualTo(HttpStatus.FORBIDDEN);

            ResponseEntity<ApiError> approveResponse = restTemplate().exchange(
                    "/api/registration-requests/" + registrationRequest.getId() + "/approve", HttpMethod.POST,
                    new HttpEntity<>(new RegistrationRequestApproveRequest(Role.PARENT, null, null), authHeaderFor(user)),
                    ApiError.class);
            assertThat(approveResponse.getStatusCode()).as("role=%s approve", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void list_asAdmin_returns200() {
        newPendingRequest(uniqueEmail());
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<String> response = restTemplate().exchange(
                "/api/registration-requests", HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"content\"").doesNotContain("passwordHash");
    }

    // ---- Rate limit: 6. istek 429 ----
    @Test
    void createRequest_exceedingAttemptLimit_returns429() {
        String email = uniqueEmail();
        RegistrationRequestCreateRequest request =
                new RegistrationRequestCreateRequest("Ad", "Soyad", email, "Rate-Limit-Pass1!", "Rate-Limit-Pass1!");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<Void> attempt = restTemplate().postForEntity("/api/auth/register-requests", request, Void.class);
            assertThat(attempt.getStatusCode()).as("deneme #%d", i + 1).isEqualTo(HttpStatus.ACCEPTED);
        }

        ResponseEntity<ApiError> sixthAttempt = restTemplate().postForEntity(
                "/api/auth/register-requests", request, ApiError.class);
        assertThat(sixthAttempt.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private RegistrationRequest newEmailVerifiedRequest(String email) {
        RegistrationRequest registrationRequest = newPendingRequest(email);
        registrationRequest.setStatus(RegistrationRequestStatus.EMAIL_VERIFIED);
        registrationRequest.setEmailVerifiedAt(Instant.now());
        return registrationRequestRepository.save(registrationRequest);
    }

    private RegistrationRequest newPendingRequest(String email) {
        RegistrationRequest registrationRequest = new RegistrationRequest();
        registrationRequest.setFirstName("Test");
        registrationRequest.setLastName("Kullanıcı");
        registrationRequest.setEmail(email);
        registrationRequest.setPasswordHash(passwordEncoder.encode(TestUsers.DEFAULT_RAW_PASSWORD));
        registrationRequest.setStatus(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION);
        return registrationRequestRepository.save(registrationRequest);
    }
}
