package com.okulservisi.backend.report.dto.response;

import java.time.LocalDate;

/** docs/API.md "14. Dashboard / Rapor" — {@code GET /api/reports/operations/daily-summary?date=}. */
public record DailySummaryResponse(
        LocalDate businessDate,
        long totalTrips,
        long plannedCount,
        long inProgressCount,
        long completedCount,
        long cancelledCount,
        long boardingCount,
        long dropoffCount,
        long absenceCount) {
}
