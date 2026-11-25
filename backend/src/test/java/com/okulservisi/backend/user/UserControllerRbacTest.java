package com.okulservisi.backend.user;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.dto.request.UserCreateRequest;
import com.okulservisi.backend.user.dto.response.UserCreateResponse;
import com.okulservisi.backend.user.dto.response.UserResponse;
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
 * docs/RBAC_MATRIX.md: "{@code /api/users/**} (kullanıcı CRUD, rol atama, aktif/pasif) — ADMIN: ✅,
 * DRIVER/HOSTESS/PARENT/STUDENT: ❌". docs/TEST_PLAN.md eşleştirmesi: T-04, T-05.
 *
 * <p>Backend Agent'ın bu turda eklediği {@code UserController}/{@code UserService} kapsamını
 * doğrular - önceki turda bu endpoint yoktu (BLOCKED), bu turda gerçek Testcontainers Postgres'e
 * karşı yazıldı ve çalıştırıldı.
 */
class UserControllerRbacTest extends AbstractIntegrationTest {

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

    // ---- T-04 ----
    @Test
    void listUsers_asDriver_returns403() {
        User driver = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/users", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listUsers_asParentOrHostessOrStudent_returns403() {
        for (Role role : new Role[] {Role.PARENT, Role.HOSTESS, Role.STUDENT}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);
            ResponseEntity<ApiError> response = restTemplate().exchange(
                    "/api/users", HttpMethod.GET, new HttpEntity<>(authHeaderFor(user)), ApiError.class);
            assertThat(response.getStatusCode()).as("role=%s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ---- T-05 ----
    @Test
    void listUsers_asAdmin_returns200() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<String> response = restTemplate().exchange(
                "/api/users", HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"content\"").contains("\"totalElements\"");
    }

    @Test
    void createUser_asAdmin_withoutPassword_generatesTemporaryPasswordAndHidesHash() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        String newEmail = "created-" + UUID.randomUUID() + "@ssp-test.local";
        UserCreateRequest request = new UserCreateRequest("Yeni Şoför", newEmail, "5551234567", Role.DRIVER, null);

        ResponseEntity<UserCreateResponse> response = restTemplate().exchange(
                "/api/users", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), UserCreateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserCreateResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.user().email()).isEqualToIgnoringCase(newEmail);
        assertThat(body.user().role()).isEqualTo(Role.DRIVER);
        assertThat(body.temporaryPassword()).isNotBlank();
    }

    @Test
    void createUser_withDuplicateEmail_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User existing = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        UserCreateRequest request = new UserCreateRequest("Çakışan", existing.getEmail(), null, Role.PARENT, null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/users", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getUserById_asAdmin_returns200WithoutPasswordHash() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);

        ResponseEntity<String> response = restTemplate().exchange(
                "/api/users/" + target.getId(), HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("passwordHash").doesNotContain(target.getPasswordHash());
    }
}
