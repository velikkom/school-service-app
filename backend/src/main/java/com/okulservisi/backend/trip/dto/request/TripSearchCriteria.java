package com.okulservisi.backend.trip.dto.request;

import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.trip.entity.TripStatus;
import java.time.LocalDate;

/**
 * {@code GET /api/trips} liste filtreleri için iç (controller->service) taşıyıcı — JSON sözleşmesinin
 * bir parçası değildir, sadece opsiyonel query parametrelerini tek bir nesnede toplar.
 */
public record TripSearchCriteria(
        Long routeId,
        Long vehicleId,
        Long driverId,
        Long hostessId,
        LocalDate businessDate,
        RouteDirection direction,
        TripStatus status) {
}
