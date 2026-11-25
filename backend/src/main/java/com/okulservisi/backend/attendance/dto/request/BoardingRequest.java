package com.okulservisi.backend.attendance.dto.request;

import jakarta.validation.constraints.NotNull;

/** docs/API.md "10. Biniş/İniş/Devamsızlık": {@code POST .../boardings { studentId }}. */
public record BoardingRequest(@NotNull(message = "studentId zorunludur.") Long studentId) {
}
