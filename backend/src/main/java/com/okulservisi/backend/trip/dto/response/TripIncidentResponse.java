package com.okulservisi.backend.trip.dto.response;

import com.okulservisi.backend.trip.entity.TripIncidentStatus;
import com.okulservisi.backend.trip.entity.TripIncidentType;
import com.okulservisi.backend.user.entity.Role;
import java.time.Instant;

/** docs/API.md §23. */
public record TripIncidentResponse(
        Long id,
        Long tripId,
        TripIncidentType type,
        TripIncidentStatus status,
        Integer delayMinutes,
        String note,
        String reportedByFullName,
        Role reportedByRole,
        Instant createdAt,
        String resolutionNote,
        String resolvedByFullName,
        Instant resolvedAt) {
}
