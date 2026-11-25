package com.okulservisi.backend.attendance.dto.response;

import java.time.Instant;

/** Roster/günlük servis satırında aktif veli bildirimi özeti (docs/API.md §22). */
public record AbsenceNoticeSummary(Long id, String note, Instant createdAt, String parentFullName) {
}
