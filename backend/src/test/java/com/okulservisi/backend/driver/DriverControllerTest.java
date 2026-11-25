package com.okulservisi.backend.driver;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.driver.dto.request.DriverCreateRequest;
import com.okulservisi.backend.driver.dto.response.DriverResponse;
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
 * docs/API.md "6. Şoför/Hostes" — her Driver bir User (role=DRIVER) ile 1-1. docs/TEST_PLAN.md
 * eşleştirmesi: T-36, T-37, T-38.
 */
class DriverControllerTest extends AbstractIntegrationTest {

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
    void createDriver_withUserIdAlreadyLinked_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User driverUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        DriverCreateRequest first = new DriverCreateRequest(driverUser.getId(), "LIC-" + driverUser.getId());
        ResponseEntity<DriverResponse> firstResponse = restTemplate().exchange(
                "/api/drivers", HttpMethod.POST, new HttpEntity<>(first, authHeaderFor(admin)), DriverResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        DriverCreateRequest duplicate = new DriverCreateRequest(driverUser.getId(), "LIC-ALT-" + driverUser.getId());
        ResponseEntity<ApiError> duplicateResponse = restTemplate().exchange(
                "/api/drivers", HttpMethod.POST, new HttpEntity<>(duplicate, authHeaderFor(admin)), ApiError.class);

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- T-37 ----
    @Test
    void createDriver_withUserOfWrongRole_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        DriverCreateRequest request = new DriverCreateRequest(parentUser.getId(), "LIC-WRONG-ROLE");

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/drivers", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("userId'nin rolü DRIVER olmadığında reddedilmeli")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- T-38 ----
    @Test
    void me_asDriver_returnsOwnProfile() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User driverUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        DriverCreateRequest createRequest = new DriverCreateRequest(driverUser.getId(), "LIC-ME-" + driverUser.getId());
        restTemplate().exchange("/api/drivers", HttpMethod.POST, new HttpEntity<>(createRequest, authHeaderFor(admin)), DriverResponse.class);

        ResponseEntity<DriverResponse> response = restTemplate().exchange(
                "/api/drivers/me", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driverUser)), DriverResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().userId()).isEqualTo(driverUser.getId());
    }

    @Test
    void me_asAdmin_returns403() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/drivers/me", HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listDrivers_asHostess_returns403() {
        User hostess = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/drivers", HttpMethod.GET, new HttpEntity<>(authHeaderFor(hostess)), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
