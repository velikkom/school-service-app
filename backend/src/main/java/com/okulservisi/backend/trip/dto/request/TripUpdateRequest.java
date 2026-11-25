package com.okulservisi.backend.trip.dto.request;

import java.time.LocalTime;

/**
 * docs/API.md §21 {@code PATCH /api/trips/{id}} (ADMIN) — sadece {@code PLANNED} seferde. {@code null}
 * alan = değişmez. Hostesi kaldırmak için {@code removeHostess=true} (hostessId ile birlikte
 * gönderilemez). Güzergâh/tarih/yön bu uçtan değiştirilemez (yolcu listesini değiştirir — yeni sefer
 * açılmalıdır).
 */
public record TripUpdateRequest(
        LocalTime plannedStartTime,
        Long vehicleId,
        Long driverId,
        Long hostessId,
        Boolean removeHostess) {
}
