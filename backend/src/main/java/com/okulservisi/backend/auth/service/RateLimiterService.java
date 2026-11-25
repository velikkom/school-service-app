package com.okulservisi.backend.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Basit in-memory, kaydırmalı pencere (sliding window) rate limiter. docs/SECURITY.md kuralı:
 * "Login ve reset-password endpoint'lerinde IP+email bazlı deneme sınırlaması (örn. 5 deneme /
 * 15 dk, aşılırsa 429)". Tek instance (tek uygulama süreci) için yeterlidir; yatay ölçekleme
 * gerekirse Redis gibi paylaşılan bir store'a taşınabilir (şu an için aşırı mühendislik olurdu).
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;

    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    /** @return true ise istek izinli (ve bir deneme olarak kaydedilir); false ise limit aşıldı. */
    public boolean tryConsume(String ip, String email) {
        String key = ip + "|" + email.toLowerCase();
        Instant now = clock.instant();
        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(now.minus(WINDOW))) {
                attempts.pollFirst();
            }
            if (attempts.size() >= MAX_ATTEMPTS) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }

    /** Başarılı girişte o anahtarın deneme sayacını sıfırlar. */
    public void reset(String ip, String email) {
        attemptsByKey.remove(ip + "|" + email.toLowerCase());
    }
}
