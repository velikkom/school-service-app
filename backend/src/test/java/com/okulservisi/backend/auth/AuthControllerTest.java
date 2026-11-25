package com.okulservisi.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.dto.request.ChangePasswordRequest;
import com.okulservisi.backend.auth.dto.request.ForgotPasswordRequest;
import com.okulservisi.backend.auth.dto.request.LoginRequest;
import com.okulservisi.backend.auth.dto.response.LoginResponse;
import com.okulservisi.backend.auth.dto.request.RefreshRequest;
import com.okulservisi.backend.auth.security.JwtProperties;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "1. Auth" ve docs/SECURITY.md kapsamını doğrulayan entegrasyon testleri.
 * docs/TEST_PLAN.md eşleştirmesi: T-01, T-02, T-03, T-06, T-07, T-08, T-17, T-18.
 *
 * <p>Gerçek, izole Testcontainers PostgreSQL'e karşı gerçek HTTP istekleriyle (TestRestTemplate,
 * RANDOM_PORT) çalışır. Her test benzersiz (UUID'li) e-posta ile kendi kullanıcısını oluşturur.
 */
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    // ---- "Beni hatırla" (docs/SECURITY.md) ----
    @Test
    void login_withoutRememberMe_usesShortRefreshTokenTtl() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Instant beforeLogin = Instant.now();

        ResponseEntity<LoginResponse> response = restTemplate().postForEntity(
                "/api/auth/login",
                new LoginRequest(user.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, false),
                LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.refreshTokenExpiresAt()).isNotNull();
        long ttlDays = jwtProperties.getRefreshTokenTtlDays();
        Instant expectedExpiry = beforeLogin.plus(ttlDays, ChronoUnit.DAYS);
        // Toleranslı aralık: istek/claim süresi nedeniyle tam saniyeye kadar eşitlik aranmaz.
        assertThat(body.refreshTokenExpiresAt())
                .isAfter(expectedExpiry.minusSeconds(30))
                .isBefore(expectedExpiry.plusSeconds(30));
    }

    @Test
    void login_withRememberMeTrue_usesLongRefreshTokenTtl() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Instant beforeLogin = Instant.now();

        ResponseEntity<LoginResponse> response = restTemplate().postForEntity(
                "/api/auth/login",
                new LoginRequest(user.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, true),
                LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.refreshTokenExpiresAt()).isNotNull();
        long ttlDays = jwtProperties.getRefreshTokenTtlDaysRememberMe();
        Instant expectedExpiry = beforeLogin.plus(ttlDays, ChronoUnit.DAYS);
        assertThat(body.refreshTokenExpiresAt())
                .isAfter(expectedExpiry.minusSeconds(30))
                .isBefore(expectedExpiry.plusSeconds(30));
        // rememberMe=true her zaman rememberMe=false/omitted'tan daha uzun bir tavan vermeli (gerçek davranış farkı).
        assertThat(body.refreshTokenExpiresAt())
                .isAfter(beforeLogin.plus(jwtProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS));
    }

    // ---- T-01 ----
    @Test
    void login_withValidCredentials_returnsTokensAndUser() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<LoginResponse> response = restTemplate().postForEntity(
                "/api/auth/login",
                new LoginRequest(user.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, null),
                LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotBlank();
        assertThat(body.user().id()).isEqualTo(user.getId());
        assertThat(body.user().email()).isEqualToIgnoringCase(user.getEmail());
        assertThat(body.user().role()).isEqualTo(Role.PARENT);
    }

    // ---- T-02 ----
    @Test
    void login_withWrongPassword_returns401WithGenericMessage() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<ApiError> response = restTemplate().postForEntity(
                "/api/auth/login",
                new LoginRequest(user.getEmail(), "wrong-password-xyz", null),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("UNAUTHORIZED");
        // Kullanıcı var/yok ayrımı sızdırılmamalı (SECURITY.md) - aynı generic mesaj.
        assertThat(response.getBody().message()).isEqualTo("E-posta veya şifre hatalı.");
    }

    @Test
    void login_withNonExistentEmail_returnsSameGenericMessageAsWrongPassword() {
        ResponseEntity<ApiError> response = restTemplate().postForEntity(
                "/api/auth/login",
                new LoginRequest("no-such-user-" + java.util.UUID.randomUUID() + "@ssp-test.local", "whatever123", null),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("E-posta veya şifre hatalı.");
    }

    // ---- T-03 ----
    @Test
    void protectedEndpoint_withoutToken_returns401() {
        ResponseEntity<ApiError> response = restTemplate().getForEntity("/api/auth/me", ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer not-a-real-jwt");
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/auth/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- T-06 ----
    @Test
    void login_whenUserInactive_returns401() {
        User inactiveUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, false);

        ResponseEntity<ApiError> response = restTemplate().postForEntity(
                "/api/auth/login",
                new LoginRequest(inactiveUser.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, null),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("E-posta veya şifre hatalı.");
    }

    // ---- T-07 ----
    @Test
    void refresh_whenUserDeactivatedMidSession_returns401() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);
        LoginResponse login = restTemplate().postForEntity(
                        "/api/auth/login",
                        new LoginRequest(user.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, null),
                        LoginResponse.class)
                .getBody();
        assertThat(login).isNotNull();

        // ADMIN kullanıcıyı pasife alır (burada doğrudan repository ile simüle edilir - /api/users
        // endpoint'i bu turda henüz yok, bkz. docs/TEST_PLAN.md T-13..T-16 BLOCKED notu).
        user.setActive(false);
        userRepository.save(user);

        ResponseEntity<ApiError> refreshResponse = restTemplate().postForEntity(
                "/api/auth/refresh", new RefreshRequest(login.refreshToken()), ApiError.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void me_whenUserDeactivatedMidSession_returns401EvenWithStillValidAccessToken() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);
        LoginResponse login = restTemplate().postForEntity(
                        "/api/auth/login",
                        new LoginRequest(user.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, null),
                        LoginResponse.class)
                .getBody();
        assertThat(login).isNotNull();

        user.setActive(false);
        userRepository.save(user);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + login.accessToken());
        ResponseEntity<ApiError> meResponse = restTemplate().exchange(
                "/api/auth/me", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), ApiError.class);

        // JwtAuthenticationFilter her istekte isActive'i DB'den tazeler (docs/SECURITY.md).
        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- T-08 ----
    @Test
    void changePassword_invalidatesAllExistingRefreshTokens() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        LoginResponse login = restTemplate().postForEntity(
                        "/api/auth/login",
                        new LoginRequest(user.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, null),
                        LoginResponse.class)
                .getBody();
        assertThat(login).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + login.accessToken());
        ResponseEntity<Void> changeResponse = restTemplate().exchange(
                "/api/auth/change-password", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(new ChangePasswordRequest(TestUsers.DEFAULT_RAW_PASSWORD, "New-Str0ng-Pass!"), headers),
                Void.class);
        assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Eski refresh token artık geçersiz olmalı.
        ResponseEntity<ApiError> refreshResponse = restTemplate().postForEntity(
                "/api/auth/refresh", new RefreshRequest(login.refreshToken()), ApiError.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Yeni şifreyle login çalışmalı.
        ResponseEntity<LoginResponse> newLogin = restTemplate().postForEntity(
                "/api/auth/login", new LoginRequest(user.getEmail(), "New-Str0ng-Pass!", null), LoginResponse.class);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void changePassword_withWrongCurrentPassword_returns400() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        LoginResponse login = restTemplate().postForEntity(
                        "/api/auth/login",
                        new LoginRequest(user.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, null),
                        LoginResponse.class)
                .getBody();
        assertThat(login).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + login.accessToken());
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/auth/change-password", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(new ChangePasswordRequest("wrong-current-password", "New-Str0ng-Pass!"), headers),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- T-17 ----
    @Test
    void forgotPassword_alwaysReturns202RegardlessOfUserExistence() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<Void> knownUserResponse = restTemplate().postForEntity(
                "/api/auth/forgot-password", new ForgotPasswordRequest(user.getEmail()), Void.class);
        ResponseEntity<Void> unknownUserResponse = restTemplate().postForEntity(
                "/api/auth/forgot-password",
                new ForgotPasswordRequest("ghost-" + java.util.UUID.randomUUID() + "@ssp-test.local"),
                Void.class);

        assertThat(knownUserResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknownUserResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    // ---- T-18 ----
    @Test
    void login_exceedingAttemptLimit_returns429() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        LoginRequest wrongPassword = new LoginRequest(user.getEmail(), "definitely-wrong", null);

        // RateLimiterService.MAX_ATTEMPTS = 5 (docs/SECURITY.md: 5 deneme / 15 dk).
        for (int i = 0; i < 5; i++) {
            ResponseEntity<ApiError> attempt = restTemplate().postForEntity("/api/auth/login", wrongPassword, ApiError.class);
            assertThat(attempt.getStatusCode())
                    .as("deneme #%d 401 (UNAUTHORIZED) olmalı, limit aşılmamış", i + 1)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<ApiError> sixthAttempt = restTemplate().postForEntity("/api/auth/login", wrongPassword, ApiError.class);
        assertThat(sixthAttempt.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
