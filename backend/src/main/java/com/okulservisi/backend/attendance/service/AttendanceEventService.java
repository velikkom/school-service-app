package com.okulservisi.backend.attendance.service;

import com.okulservisi.backend.attendance.dto.response.TripRosterEntryResponse;
import com.okulservisi.backend.attendance.entity.AttendanceEvent;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;

/**
 * docs/API.md "10. Biniş/İniş/Devamsızlık" (bkz. docs/RBAC_MATRIX.md, docs/DATABASE_DESIGN.md "Sefer
 * / Yoklama"). Yetki: ADMIN her zaman; DRIVER/HOSTESS SADECE kendi atandığı sefer için.
 */
public interface AttendanceEventService {

    AttendanceEvent recordBoarding(Long tripId, Long studentId, AuthenticatedUser principal);

    AttendanceEvent recordDropoff(Long tripId, Long studentId, AuthenticatedUser principal);

    AttendanceEvent recordAbsence(Long tripId, Long studentId, String reason, AuthenticatedUser principal);

    List<AttendanceEvent> getStudentAttendance(Long studentId, Instant from, Instant to, AuthenticatedUser principal);

    List<TripRosterEntryResponse> getRoster(Long tripId, AuthenticatedUser principal);
}
