package com.okulservisi.backend.route.dto.response;

import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StopChangeRequestStatus;
import java.time.Instant;
import java.time.LocalDate;

/** docs/API.md §25. {@code currentStopName}: talebin yönündeki BUGÜNKÜ aktif durak (yoksa null). */
public record StopChangeRequestResponse(
        Long id,
        Long studentId,
        String studentFullName,
        RouteDirection direction,
        String currentStopName,
        Long requestedStopId,
        String requestedStopName,
        String requestedRouteName,
        LocalDate effectiveDate,
        String reason,
        StopChangeRequestStatus status,
        String decisionNote,
        Instant decidedAt,
        Instant createdAt,
        String requestedByFullName,
        Long resultingAssignmentId) {
}
