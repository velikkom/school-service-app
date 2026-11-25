package com.okulservisi.backend.route.dto.request;

import com.okulservisi.backend.route.entity.RouteDirection;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** docs/API.md "8. Öğrenci atamaları": {@code { studentId, routeId, stopId, direction, validFrom, validTo? }}. */
public record AssignmentCreateRequest(
        @NotNull(message = "studentId zorunludur.") Long studentId,
        @NotNull(message = "routeId zorunludur.") Long routeId,
        @NotNull(message = "stopId zorunludur.") Long stopId,
        @NotNull(message = "direction zorunludur.") RouteDirection direction,
        @NotNull(message = "validFrom zorunludur.") LocalDate validFrom,
        LocalDate validTo) {
}
