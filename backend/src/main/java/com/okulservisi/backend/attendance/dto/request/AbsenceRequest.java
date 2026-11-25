package com.okulservisi.backend.attendance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/API.md "10. Biniş/İniş/Devamsızlık": {@code POST .../absences { studentId, reason? }}. */
public record AbsenceRequest(
        @NotNull(message = "studentId zorunludur.") Long studentId,
        @Size(max = 255, message = "reason en fazla 255 karakter olabilir.") String reason) {
}
