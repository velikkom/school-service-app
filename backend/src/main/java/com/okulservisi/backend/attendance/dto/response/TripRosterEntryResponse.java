package com.okulservisi.backend.attendance.dto.response;

import java.time.LocalTime;

/**
 * docs/API.md "9. Sefer": {@code GET /api/trips/{id}/roster} dizi elemanı — durak sırasına
 * ({@code sequenceNo}) göre sıralı döner. 2026-11-18: {@code stopPlannedTime}, aktif veli "servise
 * binmeyecek" bildirimi ({@code absenceNotice}, resmi yoklamayı DEĞİŞTİRMEZ) ve bekleyen yoklama
 * düzeltme talebi id'si ({@code pendingCorrectionId}) eklendi.
 */
public record TripRosterEntryResponse(
        Long studentId,
        String studentFullName,
        Long stopId,
        String stopName,
        Integer sequenceNo,
        LocalTime stopPlannedTime,
        RosterStatus status,
        AbsenceNoticeSummary absenceNotice,
        Long pendingCorrectionId) {
}
