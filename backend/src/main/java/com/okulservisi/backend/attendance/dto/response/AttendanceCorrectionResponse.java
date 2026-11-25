package com.okulservisi.backend.attendance.dto.response;

import com.okulservisi.backend.attendance.entity.CorrectionKind;
import com.okulservisi.backend.attendance.entity.CorrectionStatus;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.user.entity.Role;
import java.time.Instant;
import java.time.LocalDate;

/**
 * docs/API.md §24. {@code currentStatus} her yanıtta CANLI hesaplanır; {@code stale=true} ise talep
 * açıldıktan sonra kaynak kayıt değişmiştir (bekleyen talep onaylanamaz).
 */
public record AttendanceCorrectionResponse(
        Long id,
        Long tripId,
        LocalDate tripBusinessDate,
        RouteDirection tripDirection,
        Long studentId,
        String studentFullName,
        CorrectionKind kind,
        RosterStatus sourceStatus,
        RosterStatus requestedStatus,
        RosterStatus currentStatus,
        boolean stale,
        String reason,
        CorrectionStatus status,
        String requestedByFullName,
        Role requestedByRole,
        Instant createdAt,
        String decidedByFullName,
        Instant decidedAt,
        String decisionNote,
        RosterStatus appliedStatus) {
}
