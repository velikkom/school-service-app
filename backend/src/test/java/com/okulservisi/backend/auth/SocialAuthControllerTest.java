package com.okulservisi.backend.auth;
import com.okulservisi.backend.auth.entity.OAuthIdentity;
import com.okulservisi.backend.auth.entity.OAuthProvider;
import com.okulservisi.backend.auth.entity.RegistrationRequest;
import com.okulservisi.backend.auth.entity.RegistrationRequestStatus;
import com.okulservisi.backend.auth.repository.OAuthIdentityRepository;
import com.okulservisi.backend.auth.repository.RegistrationRequestRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.dto.request.SocialLinkRequest;
import com.okulservisi.backend.auth.dto.request.SocialLoginRequest;
import com.okulservisi.backend.auth.dto.response.SocialLoginResponse;
import com.okulservisi.backend.auth.dto.response.SocialLoginStatus;
import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.util.List;
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
 * docs/SECURITY.md "Sosyal giriş (Google/Facebook/X)" bölümündeki 3 senaryoyu + {@code social-link}
 * akışını doğrulayan gerçek, izole Testcontainers Postgres entegrasyon testleri. OAuth2/PKCE
 * protokol kodu (BFF'te) bu testlerin KAPSAMI DIŞINDA — sadece backend'in
 * {@code /api/auth/social-login}/{@code /api/auth/social-link} davranışı test edilir.
 */
class SocialAuthControllerTest extends AbstractIntegrationTest {

    // application-test.yml "app.security.internal-service-key" ile AYNI olmalı.
    private static final String INTERNAL_SERVICE_KEY = "test-only-internal-service-key-32bytes";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OAuthIdentityRepository oAuthIdentityRepository;

    @Autowired
    private RegistrationRequestRepository registrationRequestRepository;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private HttpHeaders internalServiceHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service-Key", INTERNAL_SERVICE_KEY);
        return headers;
    }

    private ResponseEntity<SocialLoginResponse> postSocialLogin(SocialLoginRequest request) {
        return restTemplate().exchange("/api/auth/social-login", HttpMethod.POST,
                new HttpEntity<>(request, internalServiceHeader()), SocialLoginResponse.class);
    }

    private String uniqueProviderUserId() {
        return "google-uid-" + UUID.randomUUID();
    }

    private String uniqueEmail() {
        return "social-" + UUID.randomUUID() + "@ssp-test.local";
    }

    // ---- Senaryo 2: yeni kimlik + yeni e-posta -> PENDING_APPROVAL + registration_request oluşturulur ----
    @Test
    void socialLogin_newIdentityNewEmail_returnsPendingApprovalAndCreatesRegistrationRequest() {
        String providerUserId = uniqueProviderUserId();
        String email = uniqueEmail();
        SocialLoginRequest request = new SocialLoginRequest(OAuthProvider.GOOGLE, providerUserId, email, true, "Deniz Can");

        ResponseEntity<SocialLoginResponse> response = postSocialLogin(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SocialLoginResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(SocialLoginStatus.PENDING_APPROVAL);
        assertThat(body.accessToken()).isNull();
        assertThat(body.refreshToken()).isNull();

        List<RegistrationRequest> matches = registrationRequestRepository.findByEmailIgnoreCaseAndStatusIn(
                email, List.of(RegistrationRequestStatus.EMAIL_VERIFIED, RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION));
        assertThat(matches).hasSize(1);
        // emailVerified=true gönderildi - sağlayıcı zaten doğruladığı için kendi mailimiz gönderilmeden
        // doğrudan EMAIL_VERIFIED ile açılmalı (ADMIN direkt onaylayabilsin).
        assertThat(matches.get(0).getStatus()).isEqualTo(RegistrationRequestStatus.EMAIL_VERIFIED);

        OAuthIdentity identity = oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId)
                .orElseThrow();
        assertThat(identity.getUser()).isNull();
        assertThat(identity.getRegistrationRequest()).isNotNull();
        assertThat(identity.getRegistrationRequest().getId()).isEqualTo(matches.get(0).getId());
    }

    // ---- Senaryo 3: yeni kimlik + mevcut AKTİF e-posta -> NEEDS_LINKING, token YOK, registration_request OLUŞMAZ ----
    @Test
    void socialLogin_newIdentityExistingActiveEmail_returnsNeedsLinkingWithoutCreatingRequestOrTokens() {
        User existingActiveUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String providerUserId = uniqueProviderUserId();
        SocialLoginRequest request =
                new SocialLoginRequest(OAuthProvider.GOOGLE, providerUserId, existingActiveUser.getEmail(), true, "Var Olan Kullanıcı");

        ResponseEntity<SocialLoginResponse> response = postSocialLogin(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SocialLoginResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(SocialLoginStatus.NEEDS_LINKING);
        assertThat(body.accessToken()).isNull();
        assertThat(body.refreshToken()).isNull();
        assertThat(body.message()).isNotBlank();

        assertThat(oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId)).isEmpty();
        List<RegistrationRequest> matches = registrationRequestRepository.findByEmailIgnoreCaseAndStatusIn(
                existingActiveUser.getEmail(), List.of(RegistrationRequestStatus.PENDING_EMAIL_VERIFICATION,
                        RegistrationRequestStatus.EMAIL_VERIFIED));
        assertThat(matches).isEmpty();
    }

    // ---- Senaryo 1: zaten bağlı kimlik -> AUTHENTICATED + gerçek access/refresh token ----
    @Test
    void socialLogin_withAlreadyLinkedIdentity_returnsAuthenticatedWithRealTokens() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String providerUserId = uniqueProviderUserId();
        OAuthIdentity identity = new OAuthIdentity();
        identity.setProvider(OAuthProvider.GOOGLE);
        identity.setProviderUserId(providerUserId);
        identity.setEmail(user.getEmail());
        identity.setUser(user);
        oAuthIdentityRepository.save(identity);

        SocialLoginRequest request =
                new SocialLoginRequest(OAuthProvider.GOOGLE, providerUserId, user.getEmail(), true, user.getFullName());
        ResponseEntity<SocialLoginResponse> response = postSocialLogin(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SocialLoginResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(SocialLoginStatus.AUTHENTICATED);
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotBlank();
        assertThat(body.refreshTokenExpiresAt()).isNotNull();
        assertThat(body.user()).isNotNull();
        assertThat(body.user().id()).isEqualTo(user.getId());
    }

    // ---- X-Internal-Service-Key zorunluluğu (bkz. docs/SECURITY.md) ----
    @Test
    void socialLogin_withoutInternalServiceKeyHeader_returns401AndDoesNotAuthenticateOrCreateRequest() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String providerUserId = uniqueProviderUserId();
        OAuthIdentity identity = new OAuthIdentity();
        identity.setProvider(OAuthProvider.GOOGLE);
        identity.setProviderUserId(providerUserId);
        identity.setEmail(user.getEmail());
        identity.setUser(user);
        oAuthIdentityRepository.save(identity);

        SocialLoginRequest request =
                new SocialLoginRequest(OAuthProvider.GOOGLE, providerUserId, user.getEmail(), true, user.getFullName());

        ResponseEntity<ApiError> noHeaderResponse = restTemplate().postForEntity(
                "/api/auth/social-login", request, ApiError.class);
        assertThat(noHeaderResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders wrongKey = new HttpHeaders();
        wrongKey.set("X-Internal-Service-Key", "yanlis-anahtar");
        ResponseEntity<ApiError> wrongKeyResponse = restTemplate().exchange(
                "/api/auth/social-login", HttpMethod.POST, new HttpEntity<>(request, wrongKey), ApiError.class);
        assertThat(wrongKeyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- social-link ----
    @Test
    void socialLink_newIdentity_bindsToAuthenticatedUser() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String providerUserId = uniqueProviderUserId();

        ResponseEntity<Void> response = restTemplate().exchange(
                "/api/auth/social-link", HttpMethod.POST,
                new HttpEntity<>(new SocialLinkRequest(OAuthProvider.FACEBOOK, providerUserId, user.getEmail()), authHeaderFor(user)),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        OAuthIdentity identity = oAuthIdentityRepository.findByProviderAndProviderUserId(OAuthProvider.FACEBOOK, providerUserId)
                .orElseThrow();
        assertThat(identity.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void socialLink_whenAlreadyLinkedToAnotherUser_returns409() {
        User owner = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        User other = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String providerUserId = uniqueProviderUserId();
        OAuthIdentity identity = new OAuthIdentity();
        identity.setProvider(OAuthProvider.X);
        identity.setProviderUserId(providerUserId);
        identity.setEmail(owner.getEmail());
        identity.setUser(owner);
        oAuthIdentityRepository.save(identity);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/auth/social-link", HttpMethod.POST,
                new HttpEntity<>(new SocialLinkRequest(OAuthProvider.X, providerUserId, other.getEmail()), authHeaderFor(other)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void socialLink_withoutAuthentication_returns401() {
        ResponseEntity<ApiError> response = restTemplate().postForEntity(
                "/api/auth/social-link",
                new SocialLinkRequest(OAuthProvider.GOOGLE, uniqueProviderUserId(), uniqueEmail()),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
