package com.okulservisi.backend.route.dto.request;

import com.okulservisi.backend.route.entity.RouteDirection;
import java.time.LocalDate;

/** Kısmi güncelleme (PATCH, ADMIN). {@code studentId} değiştirilemez (yeni atama oluşturulmalıdır). */
public record AssignmentUpdateRequest(
        Long routeId,
        Long stopId,
        RouteDirection direction,
        LocalDate validFrom,
        LocalDate validTo) {
}
