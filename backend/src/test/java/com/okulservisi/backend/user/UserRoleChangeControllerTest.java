package com.okulservisi.backend.user;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.dto.request.LoginRequest;
import com.okulservisi.backend.auth.dto.response.LoginResponse;
import com.okulservisi.backend.auth.dto.request.RefreshRequest;
import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.repository.HostessRepository;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.dto.response.UserResponse;
import com.okulservisi.backend.user.dto.request.UserRoleChangeRequest;
import com.okulservisi.backend.user.dto.request.UserUpdateRequest;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "2. Kullanıcı yönetimi" {@code POST /api/users/{id}/role} — Sprint 1 inceleme sonrası
 * 3. tur: rol değişikliği + gerekli domain profili TEK bir veritabanı transaction'ında garanti
 * edilir (bkz. {@code UserService#changeRole}). Bu sınıf GERÇEK bir PostgreSQL'e (Testcontainers)
 * karşı, gerçek HTTP istekleriyle (TestRestTemplate, test metodunun KENDİSİ transactional DEĞİL)
 * 9 senaryoyu kanıtlar; 10. senaryo ("profil yazma aşamasında kontrollü hata → rollback") ayrı bir
 * test double gerektirdiği için {@link UserRoleChangeAtomicityTest}'tedir.
 */
class UserRoleChangeControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private HostessRepository hostessRepository;

    @Autowired
    private ParentRepository parentRepository;

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

    private ResponseEntity<UserResponse> changeRole(Long userId, Role role, String licenseNo, User admin) {
        return restTemplate().exchange("/api/users/" + userId + "/role", HttpMethod.POST,
                new HttpEntity<>(new UserRoleChangeRequest(role, licenseNo), authHeaderFor(admin)),
                UserResponse.class);
    }

    // ---- 1) Geçerli DRIVER geçişi → rol ve profil birlikte kaydedilir ----
    @Test
    void changeRole_validDriverTransition_roleAndProfilePersistedTogether() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<UserResponse> response = changeRole(target.getId(), Role.DRIVER, "B-VALID-001", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().role()).isEqualTo(Role.DRIVER);

        // Bağımsız okuma: ayrı bir repository çağrısıyla (HTTP cevabından BAĞIMSIZ) DB'den doğrula.
        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.DRIVER);
        Driver driver = driverRepository.findByUserId(target.getId()).orElseThrow();
        assertThat(driver.getLicenseNo()).isEqualTo("B-VALID-001");
        assertThat(driver.isActive()).isTrue();
    }

    // ---- 2) Eksik/geçersiz ehliyet → hiçbir kısmi değişiklik oluşmaz ----
    @Test
    void changeRole_driverWithoutLicenseNo_rejectedWithNoPartialChange() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/users/" + target.getId() + "/role", HttpMethod.POST,
                new HttpEntity<>(new UserRoleChangeRequest(Role.DRIVER, null), authHeaderFor(admin)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getRole()).as("rol DEĞİŞMEMİŞ olmalı").isEqualTo(Role.PARENT);
        assertThat(driverRepository.existsByUserId(target.getId())).as("Driver satırı OLUŞMAMALI").isFalse();
    }

    @Test
    void changeRole_driverWithBlankLicenseNo_rejectedWithNoPartialChange() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.STUDENT, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/users/" + target.getId() + "/role", HttpMethod.POST,
                new HttpEntity<>(new UserRoleChangeRequest(Role.DRIVER, "   "), authHeaderFor(admin)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
        assertThat(driverRepository.existsByUserId(target.getId())).isFalse();
    }

    // ---- 4) HOSTESS geçişi → gerekli profil tutarlıdır (ek alan gerektirmez) ----
    @Test
    void changeRole_validHostessTransition_profileCreatedConsistently() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<UserResponse> response = changeRole(target.getId(), Role.HOSTESS, null, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Hostess hostess = hostessRepository.findByUserId(target.getId()).orElseThrow();
        assertThat(hostess.isActive()).isTrue();
    }

    // ---- PARENT geçişi → profil User'dan türetilerek oluşturulur (kapsam genişletmesi: sadece DRIVER/HOSTESS değil PARENT da kontrol edildi) ----
    @Test
    void changeRole_validParentTransition_profileDerivedFromUser() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.STUDENT, true);
        target.setPhone("5551112233");
        userRepository.save(target);

        ResponseEntity<UserResponse> response = changeRole(target.getId(), Role.PARENT, null, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Parent parent = parentRepository.findByUserId(target.getId()).orElseThrow();
        assertThat(parent.getFullName()).isEqualTo(target.getFullName());
        assertThat(parent.getPhone()).isEqualTo("5551112233");
    }

    // ---- STUDENT'a geçiş: rastgele bir Student kaydı OLUŞTURULMAMALI (Student.user bağımsız/opsiyonel) ----
    @Test
    void changeRole_toStudent_doesNotFabricateStudentRow() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<UserResponse> response = changeRole(target.getId(), Role.STUDENT, null, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
        // Not: StudentRepository burada bilerek autowire edilmedi - iddia zaten "hiçbir şey
        // oluşturulmadı" olduğu için ayrı bir sorguya gerek yok; asıl kanıt, bu metodun hiçbir
        // Student-ilişkili repository çağırmaması (üretim kodunda böyle bir çağrı YOK, bkz.
        // UserService#changeRole switch ifadesindeki "default" dalı).
    }

    // ---- 5) Mevcut profil → gereksiz çoğaltma yapılmaz (reuse) ----
    @Test
    void changeRole_whenDriverProfileAlreadyExists_reusesWithoutDuplicating() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        Driver existing = new Driver();
        existing.setUser(target);
        existing.setLicenseNo("B-EXISTING-999");
        existing.setActive(false); // kasıtlı: pasif - reuse sırasında aktive edilmeli
        driverRepository.save(existing);

        // PARENT'a geçip tekrar DRIVER'a dönerek "mevcut profil yeniden kullanılsın" senaryosunu tetikle.
        assertThat(changeRole(target.getId(), Role.PARENT, null, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<UserResponse> response = changeRole(target.getId(), Role.DRIVER, null, admin);

        assertThat(response.getStatusCode())
                .as("mevcut profil varsa licenseNo TEKRAR zorunlu olmamalı")
                .isEqualTo(HttpStatus.OK);
        assertThat(driverRepository.countByUserId(target.getId()))
                .as("mükerrer Driver satırı OLUŞMAMALI")
                .isEqualTo(1);
        Driver reused = driverRepository.findByUserId(target.getId()).orElseThrow();
        assertThat(reused.getId()).isEqualTo(existing.getId());
        assertThat(reused.getLicenseNo()).as("mevcut lisans no KORUNMALI (üzerine yazılmamalı)").isEqualTo("B-EXISTING-999");
        assertThat(reused.isActive()).as("reuse sırasında pasif profil AKTİVE edilmeli").isTrue();
    }

    // ---- 6) Doğrudan API çağrısıyla profilsiz role geçiş yapılamaz (frontend'den bağımsız) ----
    @Test
    void changeRole_calledDirectlyWithoutAnyFrontend_stillEnforcesProfileRule() {
        // Bu test sınıfındaki TÜM testler zaten doğrudan HTTP (TestRestTemplate) ile çağrılıyor -
        // hiçbir frontend kod yolu YOK. Bu test, PATCH /api/users/{id}'nin ARTIK role KABUL
        // ETMEDİĞİNİ (eski genel uçtan bypass imkânsız) AYRICA kanıtlar.
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        UserUpdateRequest bypassAttempt = new UserUpdateRequest(null, null, Role.DRIVER, null);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/users/" + target.getId(), HttpMethod.PATCH,
                new HttpEntity<>(bypassAttempt, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("eski genel PATCH ucu role alanını artık KABUL ETMEMELİ")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.PARENT);
    }

    // ---- 7) Eşzamanlı istekler → son durum tutarlıdır (aynı kullanıcı, aynı hedef rol) ----
    @Test
    void changeRole_concurrentRequestsForSameUserSameTargetRole_endStateConsistent() throws Exception {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        try {
            Future<Void> futureA = executor.submit(concurrentRoleChangeTask(target.getId(), admin, readyLatch, startLatch, successCount));
            Future<Void> futureB = executor.submit(concurrentRoleChangeTask(target.getId(), admin, readyLatch, startLatch, successCount));

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            futureA.get(30, TimeUnit.SECONDS);
            futureB.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get())
                .as("aynı kullanıcı+aynı hedef rol için İKİ eşzamanlı istek de BAŞARILI olmalı (pessimistic lock serileştirir, ikincisi mevcut profili yeniden kullanır)")
                .isEqualTo(2);
        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.DRIVER);
        assertThat(driverRepository.countByUserId(target.getId()))
                .as("İKİ eşzamanlı istek SADECE BİR Driver satırı üretmeli (mükerrer yok)")
                .isEqualTo(1L);
    }

    private Callable<Void> concurrentRoleChangeTask(Long userId, User admin, CountDownLatch readyLatch,
            CountDownLatch startLatch, AtomicInteger successCount) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            ResponseEntity<UserResponse> response = changeRole(userId, Role.DRIVER, "B-CONCURRENT-" + Thread.currentThread().getId(), admin);
            if (response.getStatusCode() == HttpStatus.OK) {
                successCount.incrementAndGet();
            }
            return null;
        };
    }

    // ---- 8) Son aktif ADMIN korunur ----
    @Test
    void changeRole_lastActiveAdmin_returns409AndRoleUnchanged() {
        User lastAdmin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.isActive() && !u.getId().equals(lastAdmin.getId()))
                .forEach(u -> {
                    u.setActive(false);
                    userRepository.save(u);
                });
        assertThat(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).isEqualTo(1);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/users/" + lastAdmin.getId() + "/role", HttpMethod.POST,
                new HttpEntity<>(new UserRoleChangeRequest(Role.PARENT, null), authHeaderFor(lastAdmin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findById(lastAdmin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    // ---- 9) Başarılı rol değişikliğinde mevcut oturum iptali kuralı çalışır ----
    @Test
    void changeRole_successfulChange_revokesExistingRefreshToken() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        LoginResponse login = restTemplate().postForEntity("/api/auth/login",
                new LoginRequest(target.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, null), LoginResponse.class).getBody();
        assertThat(login).isNotNull();

        assertThat(changeRole(target.getId(), Role.HOSTESS, null, admin).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> refreshResponse = restTemplate().postForEntity(
                "/api/auth/refresh", new RefreshRequest(login.refreshToken()), ApiError.class);
        assertThat(refreshResponse.getStatusCode())
                .as("rol değişikliğinde eski refresh token iptal edilmeli")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- 10) Başarısız işlem mevcut oturum durumunu kısmen değiştirmez ----
    @Test
    void changeRole_rejectedChange_doesNotTouchExistingSession() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        LoginResponse login = restTemplate().postForEntity("/api/auth/login",
                new LoginRequest(target.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, null), LoginResponse.class).getBody();
        assertThat(login).isNotNull();

        // Reddedilecek istek: DRIVER hedefi, licenseNo YOK -> 400, hiçbir şey değişmemeli.
        ResponseEntity<ApiError> rejected = restTemplate().exchange(
                "/api/users/" + target.getId() + "/role", HttpMethod.POST,
                new HttpEntity<>(new UserRoleChangeRequest(Role.DRIVER, null), authHeaderFor(admin)), ApiError.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Mevcut refresh token HÂLÂ geçerli olmalı (reddedilen işlem oturumu iptal ETMEMİŞ).
        ResponseEntity<LoginResponse> refreshResponse = restTemplate().postForEntity(
                "/api/auth/refresh", new RefreshRequest(login.refreshToken()), LoginResponse.class);
        assertThat(refreshResponse.getStatusCode())
                .as("reddedilen (400) rol değişikliği MEVCUT oturumu iptal ETMEMELİ")
                .isEqualTo(HttpStatus.OK);
    }
}
