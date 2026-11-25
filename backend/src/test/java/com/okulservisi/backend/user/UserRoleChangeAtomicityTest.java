package com.okulservisi.backend.user;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.dto.request.UserRoleChangeRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "2. Kullanıcı yönetimi" {@code POST /api/users/{id}/role} — 10. kabul senaryosunun
 * ({@code UserRoleChangeControllerTest} Javadoc'unda "ayrı bir test double gerektirdiği için
 * burada" diye işaret edilen) karşılığı: profil YAZMA aşamasında (DB kısıtı/doğrulama DIŞINDA)
 * kontrollü bir hata oluşursa, {@code UserService#changeRole}'daki {@code @Transactional} sınırı
 * daha önce AYNI metot içinde {@code saveAndFlush} ile Postgres'e GERÇEKTEN gönderilmiş olan rol
 * ataması UPDATE'ini de geri alır mı?
 *
 * <p>Hatayı üretim API'sine bir "hata tetikleme" parametresi EKLEMEDEN, sadece bu test sınıfının
 * Spring context'inde {@link DriverRepository} bean'ini {@code @MockBean} ile değiştirip
 * {@code save(...)}'i fırlatacak şekilde ayarlayarak tetikliyoruz — üretim kodunda hiçbir
 * değişiklik yok, sadece bu testin izole context'inde bir test double var.
 *
 * <p>Rollback kanıtı testin KENDİ transaction'ına güvenmez (test metodu {@code @Transactional}
 * DEĞİL): HTTP isteği tamamlandıktan (ve `@Transactional` AOP sınırı kapandıktan) SONRA, AYRI bir
 * {@code userRepository.findById} çağrısıyla (yeni bir transaction/connection) veritabanından
 * okuyarak doğrular — bu, testin kendi rollback'inin üretim hatasını maskelemesi riskini ortadan
 * kaldırır.
 */
class UserRoleChangeAtomicityTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private DriverRepository driverRepository;

    @Test
    void changeRole_controlledFailureDuringProfileWrite_rollsBackAlreadyFlushedRoleChange() {
        when(driverRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(driverRepository.save(any())).thenThrow(
                new RuntimeException("UserRoleChangeAtomicityTest: kontrollü test double hatası (simulated failure)"));

        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User target = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        String token = jwtService.generateAccessToken(admin.getId(), admin.getEmail(), admin.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<String> response = restTemplate().exchange(
                "/api/users/" + target.getId() + "/role", HttpMethod.POST,
                new HttpEntity<>(new UserRoleChangeRequest(Role.DRIVER, "B-ROLLBACK-TEST"), headers),
                String.class);

        assertThat(response.getStatusCode())
                .as("test double'ın fırlattığı beklenmeyen RuntimeException genel handler'a düşüp 500 dönmeli")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // Bağımsız okuma (HTTP cevabından/çağrının kendi transaction'ından BAĞIMSIZ, yeni bir
        // sorgu): daha önce saveAndFlush ile Postgres'e GERÇEKTEN yazılmış olan rol ataması da
        // geri alınmış olmalı - "role ataması hiç denenmedi" değil, GERÇEK bir DB rollback.
        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getRole())
                .as("profil yazımı kontrollü hatayla başarısız olduğunda rol ESKİ değerinde KALMALI (gerçek DB rollback)")
                .isEqualTo(Role.PARENT);
    }
}
