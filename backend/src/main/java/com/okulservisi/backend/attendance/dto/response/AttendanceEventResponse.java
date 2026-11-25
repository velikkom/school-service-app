package com.okulservisi.backend.attendance.dto.response;

import com.okulservisi.backend.attendance.entity.AttendanceEventType;
import java.time.Instant;

public record AttendanceEventResponse(
        Long id,
        Long tripId,
        Long studentId,
        AttendanceEventType eventType,
        Instant recordedAt,
        Long recordedByUserId,
        String reason,
        boolean correction) {
}
