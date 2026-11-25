package com.okulservisi.backend.attendance.dto.request;

import com.okulservisi.backend.attendance.dto.response.RosterStatus;
import jakarta.validation.constraints.Size;

/** docs/API.md §24 {@code POST /api/attendance-corrections/{id}/approve} (ADMIN). */
public record AttendanceCorrectionApproveRequest(
        RosterStatus newStatus,
        @Size(max = 500, message = "Not en fazla 500 karakter olabilir.") String note) {
}
