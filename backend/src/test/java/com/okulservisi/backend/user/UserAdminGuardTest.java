package com.okulservisi.backend.user;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.dto.response.LoginResponse;
import com.okulservisi.backend.auth.dto.request.LoginRequest;
import com.okulservisi.backend.auth.dto.request.RefreshRequest;
import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.dto.request.UserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/RBAC_MATRIX.md kritik kural #2: "Son aktif ADMIN korumalıdır. Sistemde en az bir aktif
 * ADMIN kalmayacak şekilde pasife alma / rol düşürme işlemi 409 Conflict ile reddedilir." ve
 * kritik kural #4: "Rol veya parola değişikliğinde kullanıcının tüm aktif oturumları/refresh
 * token'ları geçersizleştirilir." docs/TEST_PLAN.md eşleştirmesi: T-13, T-14, T-15, T-16.
 *
 * <p>ÖNEMLİ - test izolasyonu: tüm entegrasyon test sınıfları AYNI paylaşılan Testcontainers DB'yi
 * kullanır (bkz. AbstractIntegrationTest) ve {@code AdminBootstrapRunner} + diğer test sınıfları
 * zaten başka ADMIN kullanıcılar oluşturmuş olabilir. "Son aktif ADMIN" senaryosunu DETERMİNİSTİK
 * hale getirmek için her test, kendi ADMIN'ini oluşturduktan sonra DB'deki TÜM DİĞER aktif
 * ADMIN'leri (fixture kurulumu olarak, test edilen guard'ı atlayarak) pasife alır - böylece "sadece
 * 1 aktif ADMIN var" önkoşulu, diğer test sınıflarının çalışma sırasından bağımsız olarak garanti
 * edilir.
 */
class UserAdminGuardTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    /** Fixture kurulumu: {@code keepUserId} hariç tüm aktif ADMIN'leri pasife alır (guard'ı atlayarak). */
    private void deactivateAllOtherActiveAdmins(Long keepUserId) {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.isActive() && !u.getId().equals(keepUserId))
                .forEach(u -> {
                    u.setActive(false);
                    userRepository.save(u);
                });
    }

    // ---- T-13 ----
    @Test
    void deactivateLastActiveAdmin_returns409() {
        User lastAdmin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        deactivateAllOtherActiveAdmins(lastAdmin.getId());
        assertThat(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).isEqualTo(1);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/users/" + lastAdmin.getId() + "/deactivate", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(lastAdmin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findById(lastAdmin.getId()).orElseThrow().isActive())
                .as("son aktif ADMIN pasife ALINMAMIŞ olmalı")
                .isTrue();
    }

    // ---- T-14 ----
    @Test
    void demoteLastActiveAdminRole_returns409() {
        User lastAdmin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        deactivateAllOtherActiveAdmins(lastAdmin.getId());
        assertThat(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).isEqualTo(1);

        UserUpdateRequest demote = new UserUpdateRequest(null, null, Role.DRIVER, null);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/users/" + lastAdmin.getId(), HttpMethod.PATCH,
                new HttpEntity<>(demote, authHeaderFor(lastAdmin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findById(lastAdmin.getId()).orElseThrow().getRole())
                .as("son aktif ADMIN'in rolü DÜŞÜRÜLMEMİŞ olmalı")
                .isEqualTo(Role.ADMIN);
    }

    // ---- T-15 (pozitif kontrol) ----
    @Test
    void deactivateAdmin_whenAnotherActiveAdminExists_succeeds() {
        User adminToDeactivate = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        deactivateAllOtherActiveAdmins(adminToDeactivate.getId());
        User secondAdmin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        assertThat(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).isEqualTo(2);

        ResponseEntity<UserResponseEnvelope> response = restTemplate().exchange(
                "/api/users/" + adminToDeactivate.getId() + "/deactivate", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(secondAdmin)), UserResponseEnvelope.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findById(adminToDeactivate.getId()).orElseThrow().isActive()).isFalse();
    }

    // ---- T-16 ----
    @Test
    void changingRoleOrActive_revokesAllRefreshTokens() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);

        LoginResponse login = restTemplate().postForEntity(
                        "/api/auth/login",
                        new LoginRequest(target.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, null),
                        LoginResponse.class)
                .getBody();
        assertThat(login).isNotNull();

        UserUpdateRequest deactivateTarget = new UserUpdateRequest(null, null, null, false);
        ResponseEntity<String> updateResponse = restTemplate().exchange(
                "/api/users/" + target.getId(), HttpMethod.PATCH,
                new HttpEntity<>(deactivateTarget, authHeaderFor(admin)), String.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> refreshResponse = restTemplate().postForEntity(
                "/api/auth/refresh", new RefreshRequest(login.refreshToken()), ApiError.class);
        assertThat(refreshResponse.getStatusCode())
                .as("rol/isActive değişikliğinde eski refresh token iptal edilmeli")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** JSON gövdesinin sadece deserialize edilebilirliğini doğrulamak için minimal yardımcı tip. */
    private record UserResponseEnvelope(Long id, String fullName, String email, String phone, Role role,
            boolean isActive) {
    }
}
