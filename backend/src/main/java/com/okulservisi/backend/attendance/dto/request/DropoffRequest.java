package com.okulservisi.backend.attendance.dto.request;

import jakarta.validation.constraints.NotNull;

/** docs/API.md "10. Biniş/İniş/Devamsızlık": {@code POST .../dropoffs { studentId }}. */
public record DropoffRequest(@NotNull(message = "studentId zorunludur.") Long studentId) {
}
