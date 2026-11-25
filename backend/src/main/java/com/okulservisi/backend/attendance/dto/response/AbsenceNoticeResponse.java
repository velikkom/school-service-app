package com.okulservisi.backend.attendance.dto.response;

import com.okulservisi.backend.attendance.entity.AbsenceNoticeStatus;
import java.time.Instant;

/** docs/API.md §22. */
public record AbsenceNoticeResponse(
        Long id,
        Long tripId,
        Long studentId,
        String studentFullName,
        String parentFullName,
        String note,
        AbsenceNoticeStatus status,
        Instant createdAt,
        Instant withdrawnAt) {
}
