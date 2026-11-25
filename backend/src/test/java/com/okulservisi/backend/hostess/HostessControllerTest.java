package com.okulservisi.backend.hostess;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.hostess.dto.request.HostessCreateRequest;
import com.okulservisi.backend.hostess.dto.response.HostessResponse;
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
 * docs/API.md "6. Şoför/Hostes" — her Hostess bir User (role=HOSTESS) ile 1-1. docs/TEST_PLAN.md
 * eşleştirmesi: T-36, T-37, T-38 (hostes tarafı).
 */
class HostessControllerTest extends AbstractIntegrationTest {

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

    // ---- T-36 ----
    @Test
    void createHostess_withUserIdAlreadyLinked_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User hostessUser = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);
        HostessCreateRequest request = new HostessCreateRequest(hostessUser.getId());
        ResponseEntity<HostessResponse> firstResponse = restTemplate().exchange(
                "/api/hostesses", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), HostessResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> duplicateResponse = restTemplate().exchange(
                "/api/hostesses", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- T-37 ----
    @Test
    void createHostess_withUserOfWrongRole_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User driverUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        HostessCreateRequest request = new HostessCreateRequest(driverUser.getId());

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/hostesses", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- T-38 ----
    @Test
    void me_asHostess_returnsOwnProfile() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User hostessUser = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);
        HostessCreateRequest createRequest = new HostessCreateRequest(hostessUser.getId());
        restTemplate().exchange("/api/hostesses", HttpMethod.POST, new HttpEntity<>(createRequest, authHeaderFor(admin)), HostessResponse.class);

        ResponseEntity<HostessResponse> response = restTemplate().exchange(
                "/api/hostesses/me", HttpMethod.GET, new HttpEntity<>(authHeaderFor(hostessUser)), HostessResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().userId()).isEqualTo(hostessUser.getId());
    }

    @Test
    void listHostesses_asParent_returns403() {
        User parent = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/hostesses", HttpMethod.GET, new HttpEntity<>(authHeaderFor(parent)), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
