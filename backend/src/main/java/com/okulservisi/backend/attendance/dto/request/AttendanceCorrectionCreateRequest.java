package com.okulservisi.backend.attendance.dto.request;

import com.okulservisi.backend.attendance.dto.response.RosterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * docs/API.md §24 {@code POST /api/attendance-corrections}. {@code requestedStatus}: DRIVER/HOSTESS için
 * zorunlu, PARENT (inceleme talebi) için opsiyonel.
 */
public record AttendanceCorrectionCreateRequest(
        @NotNull(message = "tripId zorunludur.") Long tripId,
        @NotNull(message = "studentId zorunludur.") Long studentId,
        RosterStatus requestedStatus,
        @NotBlank(message = "Gerekçe zorunludur.")
        @Size(max = 500, message = "Gerekçe en fazla 500 karakter olabilir.") String reason) {
}
