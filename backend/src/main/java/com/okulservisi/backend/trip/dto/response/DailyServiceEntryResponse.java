package com.okulservisi.backend.trip.dto.response;

import com.okulservisi.backend.attendance.dto.response.AbsenceNoticeSummary;
import com.okulservisi.backend.attendance.dto.response.RosterStatus;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.trip.entity.TripIncidentType;
import com.okulservisi.backend.trip.entity.TripStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * docs/API.md §26 {@code GET /api/students/{id}/daily-service} — öğrencinin bir gündeki gidiş/dönüş
 * seferi kartı ("Bugünkü Servisim"). {@code canSubmitAbsenceNotice}/{@code canWithdrawAbsenceNotice}
 * sadece PARENT için {@code true} olabilir (backend aynı kuralları POST'ta ayrıca doğrular).
 */
public record DailyServiceEntryResponse(
        Long tripId,
        RouteDirection direction,
        LocalDate businessDate,
        TripStatus status,
        LocalTime plannedStartTime,
        String routeName,
        String vehiclePlate,
        Long stopId,
        String stopName,
        LocalTime stopPlannedTime,
        RosterStatus attendanceStatus,
        AbsenceNoticeSummary absenceNotice,
        boolean canSubmitAbsenceNotice,
        boolean canWithdrawAbsenceNotice,
        IncidentSummary latestIncident,
        String cancellationReason) {

    public record IncidentSummary(TripIncidentType type, Integer delayMinutes, String note, Instant createdAt) {
    }
}
