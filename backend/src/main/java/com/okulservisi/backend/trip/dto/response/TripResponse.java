package com.okulservisi.backend.trip.dto.response;

import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.trip.entity.TripStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * docs/API.md "9. Sefer". 2026-11-18: görüntüleme alanları ({@code routeName}, {@code vehiclePlate},
 * {@code driverFullName}, {@code hostessFullName}) — HOSTESS araç uç noktasına erişemediği halde sefer
 * kartında plakayı görebilsin diye; {@code plannedStartTime}, {@code version}, {@code
 * cancellationReason}; {@code openCancellationRequestId} sadece tekil detayda doldurulur (liste
 * yanıtlarında {@code null}).
 */
public record TripResponse(
        Long id,
        Long routeId,
        String routeName,
        Long vehicleId,
        String vehiclePlate,
        Long driverId,
        String driverFullName,
        Long hostessId,
        String hostessFullName,
        LocalDate businessDate,
        RouteDirection direction,
        TripStatus status,
        LocalTime plannedStartTime,
        Instant startedAt,
        Instant completedAt,
        Integer version,
        String cancellationReason,
        Long openCancellationRequestId) {

    public TripResponse withOpenCancellationRequestId(Long requestId) {
        return new TripResponse(id, routeId, routeName, vehicleId, vehiclePlate, driverId, driverFullName,
                hostessId, hostessFullName, businessDate, direction, status, plannedStartTime, startedAt,
                completedAt, version, cancellationReason, requestId);
    }
}
