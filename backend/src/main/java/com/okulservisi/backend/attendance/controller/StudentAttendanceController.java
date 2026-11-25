package com.okulservisi.backend.attendance.controller;

import com.okulservisi.backend.attendance.api.AttendanceApiPaths;
import com.okulservisi.backend.attendance.dto.response.AttendanceEventResponse;
import com.okulservisi.backend.attendance.mapper.AttendanceEventMapper;
import com.okulservisi.backend.attendance.service.AttendanceEventService;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.security.annotation.AnyRole;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md "10. Biniş/İniş/Devamsızlık": {@code GET /api/students/{id}/attendance?from=&to=} —
 * nesne bazlı kontrol (bkz. {@link AttendanceEventService#getStudentAttendance}). Dar kapsamlı bir
 * alt-kaynak listesi olduğu için docs/API.md "Pagination" zarfı KULLANILMAZ (bkz. {@code
 * /api/parent-student-links} emsali: "liste zarfı olmadan düz dizi").
 */
@RestController
@RequestMapping(AttendanceApiPaths.STUDENT_ATTENDANCE_BASE)
@RequiredArgsConstructor
public class StudentAttendanceController {

    private final AttendanceEventService attendanceEventService;
    private final AttendanceEventMapper attendanceEventMapper;

    @GetMapping
    @AnyRole
    public List<AttendanceEventResponse> list(
            @PathVariable Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return attendanceEventService.getStudentAttendance(studentId, from, to, principal).stream()
                .map(attendanceEventMapper::toResponse)
                .toList();
    }
}
