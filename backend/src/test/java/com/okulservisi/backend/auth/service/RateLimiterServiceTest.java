package com.okulservisi.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Saf unit test (Spring context / Testcontainers YOK) — docs/SECURITY.md politikası "5 deneme /
 * 15 dk" sınırının pencere davranışını {@code Clock.fixed} enjekte ederek deterministik olarak
 * doğrular. sleep() kullanılmaz; her aşama için farklı bir sabit {@link Clock} örneği verilir.
 */
class RateLimiterServiceTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    /**
     * RateLimiterService'in tek bir Clock enjeksiyonu alması nedeniyle, test aşamaları arasında
     * zamanı ilerletmek için altındaki sabit Clock'u değiştirebilen bir vekil (delegate). Her
     * {@link #swapTo(Clock)} çağrısı gerçekten farklı, yeni bir {@code Clock.fixed(...)} örneği
     * alır — RateLimiterService içinde hâlâ sadece {@code clock.instant()} çağrılır.
     */
    private static final class SwappableClock extends Clock {
        private Clock delegate;

        private SwappableClock(Clock initial) {
            this.delegate = initial;
        }

        void swapTo(Clock next) {
            this.delegate = next;
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return delegate.withZone(zone);
        }

        @Override
        public Instant instant() {
            return delegate.instant();
        }
    }

    @Test
    void pencereIcinde5DenemedenSonra6ciReddedilir() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RateLimiterService rateLimiter = new RateLimiterService(Clock.fixed(now, ZoneOffset.UTC));

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryConsume("1.2.3.4", "test@example.com")).isTrue();
        }
        assertThat(rateLimiter.tryConsume("1.2.3.4", "test@example.com")).isFalse();
    }

    @Test
    void pencereSinirindanBirSaniyeOncesindeHalaLimitliKalir() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        SwappableClock clock = new SwappableClock(Clock.fixed(start, ZoneOffset.UTC));
        RateLimiterService rateLimiter = new RateLimiterService(clock);

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryConsume("5.6.7.8", "test2@example.com")).isTrue();
        }

        clock.swapTo(Clock.fixed(start.plus(WINDOW).minusSeconds(1), ZoneOffset.UTC));
        assertThat(rateLimiter.tryConsume("5.6.7.8", "test2@example.com")).isFalse();
    }

    @Test
    void pencereSinirindanBirSaniyeSonrasindaSifirlanirYeniDenemeKabulEdilir() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        SwappableClock clock = new SwappableClock(Clock.fixed(start, ZoneOffset.UTC));
        RateLimiterService rateLimiter = new RateLimiterService(clock);

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryConsume("9.9.9.9", "test3@example.com")).isTrue();
        }

        clock.swapTo(Clock.fixed(start.plus(WINDOW).plusSeconds(1), ZoneOffset.UTC));
        assertThat(rateLimiter.tryConsume("9.9.9.9", "test3@example.com")).isTrue();
    }

    @Test
    void basariliGiristeSayacSifirlanirVeAyniPencerededeTekrarKabulEdilir() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        RateLimiterService rateLimiter = new RateLimiterService(Clock.fixed(now, ZoneOffset.UTC));

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryConsume("10.0.0.1", "test4@example.com")).isTrue();
        }
        assertThat(rateLimiter.tryConsume("10.0.0.1", "test4@example.com")).isFalse();

        rateLimiter.reset("10.0.0.1", "test4@example.com");

        assertThat(rateLimiter.tryConsume("10.0.0.1", "test4@example.com")).isTrue();
    }
}
