package com.okulservisi.backend.trip.dto.request;

import com.okulservisi.backend.route.entity.RouteDirection;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * docs/API.md "9. Sefer": {@code { routeId, vehicleId, driverId, hostessId?, businessDate, direction,
 * plannedStartTime? }}. {@code status} KASITLI OLARAK bu DTO'da YOK — yeni sefer her zaman sunucu
 * tarafında {@code PLANNED} olarak başlar. {@code plannedStartTime} 2026-11-18'de eklendi (opsiyonel,
 * "HH:mm" veya "HH:mm:ss").
 */
public record TripCreateRequest(
        @NotNull(message = "routeId zorunludur.") Long routeId,
        @NotNull(message = "vehicleId zorunludur.") Long vehicleId,
        @NotNull(message = "driverId zorunludur.") Long driverId,
        Long hostessId,
        @NotNull(message = "businessDate zorunludur.") LocalDate businessDate,
        @NotNull(message = "direction zorunludur.") RouteDirection direction,
        LocalTime plannedStartTime) {

    /** Geriye dönük uyumlu kurucu (planlanan saat olmadan). */
    public TripCreateRequest(Long routeId, Long vehicleId, Long driverId, Long hostessId, LocalDate businessDate,
            RouteDirection direction) {
        this(routeId, vehicleId, driverId, hostessId, businessDate, direction, null);
    }
}
