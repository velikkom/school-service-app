package com.okulservisi.backend.route.dto.request;

import com.okulservisi.backend.route.entity.RouteDirection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** docs/API.md §25 {@code POST /api/stop-change-requests} (PARENT). */
public record StopChangeRequestCreateRequest(
        @NotNull(message = "studentId zorunludur.") Long studentId,
        @NotNull(message = "direction zorunludur.") RouteDirection direction,
        @NotNull(message = "requestedStopId zorunludur.") Long requestedStopId,
        @NotNull(message = "effectiveDate zorunludur.") LocalDate effectiveDate,
        @Size(max = 500, message = "Gerekçe en fazla 500 karakter olabilir.") String reason) {
}
