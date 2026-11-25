package com.okulservisi.backend.auth;
import com.okulservisi.backend.auth.entity.PasswordResetToken;
import com.okulservisi.backend.auth.repository.PasswordResetTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.dto.request.ForgotPasswordRequest;
import com.okulservisi.backend.auth.dto.request.ResetPasswordRequest;
import com.okulservisi.backend.auth.security.OpaqueTokenGenerator;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/SECURITY.md "Reset password token" kuralını doğrular: tek kullanımlık, süreli, eşzamanlı
 * çift kullanıma karşı atomik {@code usedAt IS NULL} kontrolü, yeni istek önceki kullanılmamış
 * token'ları iptal eder.
 *
 * <p>docs/TEST_PLAN.md eşleştirmesi: T-09, T-10 (gerçek concurrency), T-11, T-12.
 *
 * <p>Reset token'ın ham değerini test içinde elde etmek için {@code /api/auth/forgot-password}
 * endpoint'i yerine doğrudan repository + {@link OpaqueTokenGenerator} kullanılarak token
 * oluşturulur (ham token normalde sadece e-posta gövdesine yazılır, hash'ten geri çözülemez).
 * {@code /api/auth/forgot-password}'ün kendisi ayrı olarak {@code AuthControllerTest} içinde test
 * edilir (T-17).
 */
class PasswordResetConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private OpaqueTokenGenerator opaqueTokenGenerator;

    private String createActiveResetToken(User user, Instant expiresAt) {
        String rawToken = opaqueTokenGenerator.generate();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(opaqueTokenGenerator.hash(rawToken));
        token.setExpiresAt(expiresAt);
        passwordResetTokenRepository.save(token);
        return rawToken;
    }

    // ---- T-09 ----
    @Test
    void reusingSameResetToken_secondAttemptFails() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String rawToken = createActiveResetToken(user, Instant.now().plus(30, ChronoUnit.MINUTES));

        ResponseEntity<Void> first = restTemplate().postForEntity(
                "/api/auth/reset-password", new ResetPasswordRequest(rawToken, "First-New-Pass1!"), Void.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiError> second = restTemplate().postForEntity(
                "/api/auth/reset-password", new ResetPasswordRequest(rawToken, "Second-New-Pass2!"), ApiError.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Şifre ilk (kazanan) istekten kalmalı.
        assertThat(passwordEncoder.matches("First-New-Pass1!", userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    // ---- T-10: gerçek concurrency testi (iki istek aynı anda) ----
    @Test
    void concurrentUseOfSameResetToken_onlyOneSucceeds() throws Exception {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String rawToken = createActiveResetToken(user, Instant.now().plus(30, ChronoUnit.MINUTES));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        try {
            Future<Void> futureA = executor.submit(
                    concurrentResetTask(rawToken, "Racer-A-Pass1!", readyLatch, startLatch, successCount, failureCount));
            Future<Void> futureB = executor.submit(
                    concurrentResetTask(rawToken, "Racer-B-Pass2!", readyLatch, startLatch, successCount, failureCount));

            // İki thread de HTTP çağrısı yapmaya hazır olana kadar bekle, sonra ikisini AYNI ANDA serbest
            // bırak - gerçek eşzamanlı istek (sıralı/mock değil) elde etmenin anahtarı budur.
            boolean bothReady = readyLatch.await(5, TimeUnit.SECONDS);
            assertThat(bothReady).as("iki thread de zamanında hazır olmalı").isTrue();
            startLatch.countDown();

            futureA.get(30, TimeUnit.SECONDS);
            futureB.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get())
                .as("Tam olarak bir istek reset token'ı kazanmalı (DB seviyesinde atomik usedAt IS NULL kontrolü)")
                .isEqualTo(1);
        assertThat(failureCount.get())
                .as("Diğer eşzamanlı istek 401 ile reddedilmeli")
                .isEqualTo(1);

        PasswordResetToken persisted = passwordResetTokenRepository.findByTokenHash(opaqueTokenGenerator.hash(rawToken)).orElseThrow();
        assertThat(persisted.getUsedAt()).isNotNull();
    }

    private Callable<Void> concurrentResetTask(String rawToken, String newPassword, CountDownLatch readyLatch,
            CountDownLatch startLatch, AtomicInteger successCount, AtomicInteger failureCount) {
        return () -> {
            readyLatch.countDown();
            startLatch.await(); // tüm thread'ler aynı anda başlasın
            ResponseEntity<String> response = restTemplate().postForEntity(
                    "/api/auth/reset-password", new ResetPasswordRequest(rawToken, newPassword), String.class);
            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                successCount.incrementAndGet();
            } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                failureCount.incrementAndGet();
            }
            return null;
        };
    }

    // ---- T-11 ----
    @Test
    void newResetRequest_invalidatesPreviousUnusedTokens() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String oldRawToken = createActiveResetToken(user, Instant.now().plus(30, ChronoUnit.MINUTES));

        ResponseEntity<Void> forgotResponse = restTemplate().postForEntity(
                "/api/auth/forgot-password", new ForgotPasswordRequest(user.getEmail()), Void.class);
        assertThat(forgotResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<ApiError> resetWithOldToken = restTemplate().postForEntity(
                "/api/auth/reset-password", new ResetPasswordRequest(oldRawToken, "Whatever-New1!"), ApiError.class);

        assertThat(resetWithOldToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- T-12 ----
    @Test
    void expiredResetToken_isRejected() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String expiredRawToken = createActiveResetToken(user, Instant.now().minus(1, ChronoUnit.MINUTES));

        ResponseEntity<ApiError> response = restTemplate().postForEntity(
                "/api/auth/reset-password", new ResetPasswordRequest(expiredRawToken, "Whatever-New1!"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
