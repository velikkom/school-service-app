package com.okulservisi.backend.support;

import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test fixture yardımcı sınıfı: entegrasyon testleri için gerçek DB'de (Testcontainers) kullanıcı
 * oluşturur. Her çağrıda benzersiz (UUID tabanlı) e-posta üretir — testler arasında paylaşılan
 * container/DB üzerinde çakışma/sıra bağımlılığı olmaması için (bkz. docs/TEST_PLAN.md ortam
 * izolasyonu notu: test metotları arasında izolasyon, benzersiz fixture verisiyle sağlanır).
 */
public final class TestUsers {

    private TestUsers() {
    }

    public static final String DEFAULT_RAW_PASSWORD = "Str0ng-Test-Passw0rd!";

    public static User createUser(UserRepository userRepository, PasswordEncoder passwordEncoder,
            Role role, boolean active) {
        return createUser(userRepository, passwordEncoder, role, active, DEFAULT_RAW_PASSWORD);
    }

    public static User createUser(UserRepository userRepository, PasswordEncoder passwordEncoder,
            Role role, boolean active, String rawPassword) {
        User user = new User();
        user.setEmail("test-" + UUID.randomUUID() + "@ssp-test.local");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName("Test " + role.name());
        user.setRole(role);
        user.setActive(active);
        return userRepository.save(user);
    }
}
