package com.okulservisi.backend.school;
import com.okulservisi.backend.school.entity.School;
import com.okulservisi.backend.school.repository.SchoolRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.school.dto.request.SchoolCreateRequest;
import com.okulservisi.backend.school.dto.response.SchoolResponse;
import com.okulservisi.backend.school.dto.request.SchoolUpdateRequest;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "20. Okul" — ADMIN CRUD + PARENT salt-okunur liste (Vehicle modülüyle AYNI basit CRUD
 * deseni). {@code name} büyük/küçük harf duyarsız benzersizdir.
 */
class SchoolControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SchoolRepository schoolRepository;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private School createSchool(String namePrefix, boolean active) {
        School school = new School();
        school.setName(namePrefix + " " + UUID.randomUUID());
        school.setActive(active);
        return schoolRepository.save(school);
    }

    @Test
    void createSchool_asAdmin_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        SchoolCreateRequest request = new SchoolCreateRequest("Atatürk İlkokulu " + UUID.randomUUID(), "Adres", "0212 000 00 00");

        ResponseEntity<SchoolResponse> response = restTemplate().exchange(
                "/api/schools", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), SchoolResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo(request.name());
        assertThat(response.getBody().isActive()).isTrue();
    }

    @Test
    void createSchool_withBlankName_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        SchoolCreateRequest request = new SchoolCreateRequest("  ", null, null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/schools", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSchool_withDuplicateNameDifferentCase_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        String name = "Cumhuriyet Ortaokulu " + UUID.randomUUID();
        SchoolCreateRequest first = new SchoolCreateRequest(name, null, null);
        ResponseEntity<SchoolResponse> firstResponse = restTemplate().exchange(
                "/api/schools", HttpMethod.POST, new HttpEntity<>(first, authHeaderFor(admin)), SchoolResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        SchoolCreateRequest duplicateDifferentCase = new SchoolCreateRequest(name.toUpperCase(), null, null);
        ResponseEntity<ApiError> duplicateResponse = restTemplate().exchange(
                "/api/schools", HttpMethod.POST, new HttpEntity<>(duplicateDifferentCase, authHeaderFor(admin)), ApiError.class);

        assertThat(duplicateResponse.getStatusCode())
                .as("okul adı büyük/küçük harf duyarsız benzersiz olmalı")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateSchool_togglesIsActive() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        School school = createSchool("Pasifleştirilecek Okul", true);

        SchoolUpdateRequest request = new SchoolUpdateRequest(null, null, null, false);
        ResponseEntity<SchoolResponse> response = restTemplate().exchange(
                "/api/schools/" + school.getId(), HttpMethod.PATCH, new HttpEntity<>(request, authHeaderFor(admin)), SchoolResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isFalse();
    }

    @Test
    void updateSchool_withDuplicateName_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        School existing = createSchool("Var Olan Okul", true);
        School toUpdate = createSchool("Güncellenecek Okul", true);

        SchoolUpdateRequest request = new SchoolUpdateRequest(existing.getName(), null, null, null);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/schools/" + toUpdate.getId(), HttpMethod.PATCH, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listSchools_asAdmin_returnsAllRegardlessOfActive() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        School activeSchool = createSchool("Admin Liste Aktif", true);
        School inactiveSchool = createSchool("Admin Liste Pasif", false);

        ResponseEntity<PageResponse<SchoolResponse>> response = restTemplate().exchange(
                "/api/schools?q=Admin Liste&size=50", HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var ids = response.getBody().content().stream().map(SchoolResponse::id).toList();
        assertThat(ids).contains(activeSchool.getId(), inactiveSchool.getId());
    }

    @Test
    void listSchools_asParent_onlyReturnsActiveSchools_evenIfIsActiveFalseRequested() {
        User parent = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        School activeSchool = createSchool("Veli Liste Aktif", true);
        School inactiveSchool = createSchool("Veli Liste Pasif", false);

        ResponseEntity<PageResponse<SchoolResponse>> response = restTemplate().exchange(
                "/api/schools?q=Veli Liste&isActive=false&size=50", HttpMethod.GET, new HttpEntity<>(authHeaderFor(parent)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var ids = response.getBody().content().stream().map(SchoolResponse::id).toList();
        assertThat(ids)
                .as("PARENT isActive=false istese de pasif okul görmemeli - override her zaman true")
                .contains(activeSchool.getId())
                .doesNotContain(inactiveSchool.getId());
    }

    @Test
    void schoolEndpoints_asDriverOrHostessOrStudent_return403() {
        for (Role role : new Role[] {Role.DRIVER, Role.HOSTESS, Role.STUDENT}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);
            ResponseEntity<ApiError> response = restTemplate().exchange(
                    "/api/schools", HttpMethod.GET, new HttpEntity<>(authHeaderFor(user)), ApiError.class);
            assertThat(response.getStatusCode()).as("role=%s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void createSchool_asParent_returns403() {
        User parent = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        SchoolCreateRequest request = new SchoolCreateRequest("Veli Deneme Okulu " + UUID.randomUUID(), null, null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/schools", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(parent)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateSchool_asParent_returns403() {
        User parent = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        School school = createSchool("Veli Update Deneme", true);
        SchoolUpdateRequest request = new SchoolUpdateRequest("Yeni İsim", null, null, null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/schools/" + school.getId(), HttpMethod.PATCH, new HttpEntity<>(request, authHeaderFor(parent)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getSchool_asParent_succeeds() {
        User parent = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        School school = createSchool("Veli Detay Okulu", true);

        ResponseEntity<SchoolResponse> response = restTemplate().exchange(
                "/api/schools/" + school.getId(), HttpMethod.GET, new HttpEntity<>(authHeaderFor(parent)), SchoolResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(school.getId());
    }
}
