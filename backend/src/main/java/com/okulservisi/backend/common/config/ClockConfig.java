package com.okulservisi.backend.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sistem saatine (Instant.now()/LocalDateTime.now() vb.) doğrudan bağımlı servislerin
 * (örn. {@code RateLimiterService}) test edilebilir olması için enjekte edilebilir
 * {@link Clock} bean'i. Üretimde gerçek UTC saatini kullanır; testlerde sabit/offset
 * bir {@code Clock} ile değiştirilebilir.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
