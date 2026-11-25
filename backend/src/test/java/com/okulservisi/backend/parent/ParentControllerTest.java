package com.okulservisi.backend.parent;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.parent.dto.request.ParentCreateRequest;
import com.okulservisi.backend.parent.dto.response.ParentResponse;
import com.okulservisi.backend.parent.dto.request.ParentUpdateRequest;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "4. Veli" {@code /api/parents} — ADMIN CRUD + {@code GET /api/parents/me} (PARENT).
 * Her Parent bir User (role=PARENT) ile 1-1 ilişkilidir (docs/DATABASE_DESIGN.md).
 *
 * <p>Bu dosya eklenene kadar bu modülün CRUD pozitif kontrolleri doğrulanmamıştı (sadece
 * {@code /api/parent-student-links} ve {@code StudentController} içinde dolaylı fixture olarak
 * kullanılmıştı) — bkz. docs/PROGRESS.md "2026-10-16 — Faz E ara durum" sıradaki adım 1 ve
 * docs/TEST_PLAN.md yeni eklenen T-24b..T-24g satırları.
 */
class ParentControllerTest extends AbstractIntegrationTest {

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

    @Test
    void createParent_asAdmin_returns200() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        ParentCreateRequest request = new ParentCreateRequest(parentUser.getId(), "Yeni Veli", "05551112233", null);

        ResponseEntity<ParentResponse> response = restTemplate().exchange(
                "/api/parents", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ParentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fullName()).isEqualTo("Yeni Veli");
        assertThat(response.getBody().userId()).isEqualTo(parentUser.getId());
    }

    @Test
    void createParent_withUserAlreadyLinked_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        ParentCreateRequest request = new ParentCreateRequest(parentUser.getId(), "Veli Bir", null, null);
        restTemplate().exchange("/api/parents", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ParentResponse.class);

        ResponseEntity<ApiError> duplicateResponse = restTemplate().exchange(
                "/api/parents", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createParent_withUserOfWrongRole_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User driverUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        ParentCreateRequest request = new ParentCreateRequest(driverUser.getId(), "Yanlış Rol", null, null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parents", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminCrud_getAndUpdate_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        ParentCreateRequest createRequest = new ParentCreateRequest(parentUser.getId(), "Güncellenecek Veli", null, null);
        ResponseEntity<ParentResponse> createResponse = restTemplate().exchange(
                "/api/parents", HttpMethod.POST, new HttpEntity<>(createRequest, authHeaderFor(admin)), ParentResponse.class);
        Long parentId = createResponse.getBody().id();

        ResponseEntity<ParentResponse> getResponse = restTemplate().exchange(
                "/api/parents/" + parentId, HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)), ParentResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().id()).isEqualTo(parentId);

        ParentUpdateRequest updateRequest = new ParentUpdateRequest("Güncellenmiş Veli Adı", "05559998877", null);
        ResponseEntity<ParentResponse> updateResponse = restTemplate().exchange(
                "/api/parents/" + parentId, HttpMethod.PATCH, new HttpEntity<>(updateRequest, authHeaderFor(admin)), ParentResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().fullName()).isEqualTo("Güncellenmiş Veli Adı");
        assertThat(updateResponse.getBody().phone()).isEqualTo("05559998877");
    }

    @Test
    void me_asParent_returnsOwnProfile() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        ParentCreateRequest createRequest = new ParentCreateRequest(parentUser.getId(), "Kendi Profili", null, null);
        restTemplate().exchange("/api/parents", HttpMethod.POST, new HttpEntity<>(createRequest, authHeaderFor(admin)), ParentResponse.class);

        ResponseEntity<ParentResponse> response = restTemplate().exchange(
                "/api/parents/me", HttpMethod.GET, new HttpEntity<>(authHeaderFor(parentUser)), ParentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().userId()).isEqualTo(parentUser.getId());
    }

    @Test
    void listParents_asDriverOrHostess_returns403() {
        for (Role role : new Role[] {Role.DRIVER, Role.HOSTESS}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);
            ResponseEntity<ApiError> response = restTemplate().exchange(
                    "/api/parents", HttpMethod.GET, new HttpEntity<>(authHeaderFor(user)), ApiError.class);
            assertThat(response.getStatusCode()).as("role=%s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
