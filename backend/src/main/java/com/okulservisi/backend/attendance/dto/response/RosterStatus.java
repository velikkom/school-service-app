package com.okulservisi.backend.attendance.dto.response;

/**
 * {@code GET /api/trips/{id}/roster} — bir öğrencinin o seferdeki güncel yoklama durumu (docs/API.md
 * "9. Sefer"). Öncelik sırası {@code DROPPED_OFF > ABSENT > BOARDED > NONE}: DROPOFF kaydı varsa
 * BOARDING kaydı da vardır (bkz. {@code AttendanceEventService} iş kuralı), bu durumda iniş durumu
 * gösterilir.
 */
public enum RosterStatus {
    NONE,
    BOARDED,
    DROPPED_OFF,
    ABSENT
}
