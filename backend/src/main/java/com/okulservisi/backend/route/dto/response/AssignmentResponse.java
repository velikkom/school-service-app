package com.okulservisi.backend.route.dto.response;

import com.okulservisi.backend.route.entity.RouteDirection;
import java.time.LocalDate;

public record AssignmentResponse(
        Long id,
        Long studentId,
        Long routeId,
        Long stopId,
        RouteDirection direction,
        LocalDate validFrom,
        LocalDate validTo) {
}
