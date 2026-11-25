package com.okulservisi.backend.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.settings.dto.response.InstitutionSettingsResponse;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
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
 * docs/API.md "17. Kurum ayarları" (bkz. docs/RBAC_MATRIX.md: SADECE ADMIN).
 *
 * <p><b>ÖNEMLİ — test izolasyonu notu:</b> {@code institution_settings} TEK SATIRLIKTIR (V9
 * migration'ı tarafından seed edilir) ve tüm test SINIFLARI AYNI Testcontainers Postgres'i (bkz.
 * {@code AbstractIntegrationTest}) paylaşır — yani diğer test metotları/sınıfları da (varsa) AYNI
 * satırı günceller. Bu yüzden testler migration'ın seed ettiği varsayılan değere ASLA güvenmez;
 * her test KENDİ bilinen değerini PATCH ile yazıp hemen ardından doğrular (UUID'li benzersiz
 * değerlerle), sıra/çakışma bağımsız hale getirilir.
 */
class InstitutionSettingsControllerTest extends AbstractIntegrationTest {

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
    void get_asAdmin_returnsASingleRow() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<InstitutionSettingsResponse> response = restTemplate().exchange(
                "/api/settings", HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)),
                InstitutionSettingsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().name()).isNotBlank();
    }

    @Test
    void patch_asAdmin_updatesFields_andGetReflectsChange() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        String uniqueName = "Test Okul Servisi " + UUID.randomUUID();
        String uniqueAddress = "Adres " + UUID.randomUUID();
        String uniquePhone = "0212" + System.currentTimeMillis() % 10_000_000;
        String uniqueNote = "Not " + UUID.randomUUID();

        Map<String, Object> patch = Map.of(
                "name", uniqueName, "address", uniqueAddress, "phone", uniquePhone, "reportHeaderNote", uniqueNote);

        ResponseEntity<InstitutionSettingsResponse> patched = restTemplate().exchange(
                "/api/settings", HttpMethod.PATCH, new HttpEntity<>(patch, authHeaderFor(admin)),
                InstitutionSettingsResponse.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().name()).isEqualTo(uniqueName);
        assertThat(patched.getBody().address()).isEqualTo(uniqueAddress);
        assertThat(patched.getBody().phone()).isEqualTo(uniquePhone);
        assertThat(patched.getBody().reportHeaderNote()).isEqualTo(uniqueNote);

        ResponseEntity<InstitutionSettingsResponse> reread = restTemplate().exchange(
                "/api/settings", HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)),
                InstitutionSettingsResponse.class);
        assertThat(reread.getBody().name()).isEqualTo(uniqueName);
        assertThat(reread.getBody().phone()).isEqualTo(uniquePhone);
    }

    @Test
    void patch_partialUpdate_leavesOtherFieldsUnchanged() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        String baselineName = "Taban Ad " + UUID.randomUUID();
        String baselineAddress = "Taban Adres " + UUID.randomUUID();

        restTemplate().exchange("/api/settings", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", baselineName, "address", baselineAddress), authHeaderFor(admin)),
                InstitutionSettingsResponse.class);

        String newPhone = "0312" + System.currentTimeMillis() % 10_000_000;
        ResponseEntity<InstitutionSettingsResponse> partial = restTemplate().exchange(
                "/api/settings", HttpMethod.PATCH, new HttpEntity<>(Map.of("phone", newPhone), authHeaderFor(admin)),
                InstitutionSettingsResponse.class);

        assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(partial.getBody().phone()).isEqualTo(newPhone);
        assertThat(partial.getBody().name())
                .as("sadece phone PATCH edildi - name DEĞİŞMEMELİ")
                .isEqualTo(baselineName);
        assertThat(partial.getBody().address()).isEqualTo(baselineAddress);
    }

    @Test
    void get_asNonAdmin_returns403() {
        User driver = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/settings", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void patch_asNonAdmin_returns403() {
        User parent = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/settings", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "Yetkisiz Değişiklik"), authHeaderFor(parent)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
