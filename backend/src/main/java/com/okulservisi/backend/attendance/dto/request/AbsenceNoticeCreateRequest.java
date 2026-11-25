package com.okulservisi.backend.attendance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/API.md §22 {@code POST /api/trips/{tripId}/absence-notices} (PARENT). */
public record AbsenceNoticeCreateRequest(
        @NotNull(message = "studentId zorunludur.") Long studentId,
        @Size(max = 500, message = "Not en fazla 500 karakter olabilir.") String note) {
}
