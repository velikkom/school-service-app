package com.okulservisi.backend.auth.service;

import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * İlk ADMIN bootstrap: uygulama başlarken DB'de hiç ADMIN yoksa {@code BOOTSTRAP_ADMIN_*} ortam
 * değişkenleriyle bir ADMIN oluşturur. İdempotenttir: DB'de en az bir ADMIN varsa hiçbir şey
 * yapmaz (tekrar çalıştırmak ikinci ADMIN oluşturmaz/parolayı değiştirmez). Varsayılan sabit bir
 * parola KODDA YOKTUR — env değişkeni verilmemişse ve hiç ADMIN yoksa uygulama açıkça hata verir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.email:}")
    private String bootstrapEmail;

    @Value("${app.bootstrap-admin.password:}")
    private String bootstrapPassword;

    @Value("${app.bootstrap-admin.full-name:Sistem Yöneticisi}")
    private String bootstrapFullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long adminCount = userRepository.countByRole(Role.ADMIN);
        if (adminCount > 0) {
            log.info("Bootstrap ADMIN kontrolü: zaten {} ADMIN kullanıcı mevcut, atlanıyor.", adminCount);
            return;
        }
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            throw new IllegalStateException(
                    "Sistemde hiç ADMIN kullanıcı yok ve BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD "
                            + "tanımlı değil. İlk ADMIN oluşturulamıyor.");
        }
        if (userRepository.existsByEmailIgnoreCase(bootstrapEmail)) {
            log.warn("Bootstrap ADMIN e-postası ({}) başka bir kullanıcıda zaten kayıtlı, atlanıyor.", bootstrapEmail);
            return;
        }
        User admin = new User();
        admin.setEmail(bootstrapEmail.trim());
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setFullName(bootstrapFullName);
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
        log.info("İlk ADMIN kullanıcı oluşturuldu: {}", bootstrapEmail);
    }
}
