package com.okulservisi.backend.tracking.service;
import com.okulservisi.backend.tracking.repository.VehicleLocationRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/DATABASE_DESIGN.md "vehicle_location ... Saklama: 90 gün" — bu sınıf o kuralı uygular.
 * Billing job'ın (ayın 1'i 03:00) saatiyle çakışmaması için günlük 02:30 Europe/Istanbul'da çalışır.
 * {@code @EnableScheduling} {@code BackendApplication}'da tanımlıdır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleLocationRetentionScheduler {

    private static final long RETENTION_DAYS = 90;

    private final VehicleLocationRepository vehicleLocationRepository;
    private final Clock clock;

    @Scheduled(cron = "0 30 2 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void purgeOldLocations() {
        Instant cutoff = clock.instant().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        long deleted = vehicleLocationRepository.deleteByRecordedAtBefore(cutoff);
        log.info("vehicle_location temizlik: cutoff={} silinen={}", cutoff, deleted);
    }
}
