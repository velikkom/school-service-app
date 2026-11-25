package com.okulservisi.backend.attendance.controller;

import com.okulservisi.backend.attendance.api.AttendanceApiPaths;
import com.okulservisi.backend.attendance.dto.request.AbsenceRequest;
import com.okulservisi.backend.attendance.dto.response.AttendanceEventResponse;
import com.okulservisi.backend.attendance.dto.request.BoardingRequest;
import com.okulservisi.backend.attendance.dto.request.DropoffRequest;
import com.okulservisi.backend.attendance.entity.AttendanceEvent;
import com.okulservisi.backend.attendance.mapper.AttendanceEventMapper;
import com.okulservisi.backend.attendance.service.AttendanceEventService;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.security.annotation.AdminDriverOrHostess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md "10. Biniş/İniş/Devamsızlık" (bkz. docs/RBAC_MATRIX.md). PARENT/STUDENT bu
 * endpoint'lere erişemez (RBAC_MATRIX: ❌) — {@code @PreAuthorize} ile reddedilir.
 */
@RestController
@RequestMapping(AttendanceApiPaths.TRIP_ATTENDANCE_EVENTS_BASE)
@AdminDriverOrHostess
@RequiredArgsConstructor
public class AttendanceEventController {

    private final AttendanceEventService attendanceEventService;
    private final AttendanceEventMapper attendanceEventMapper;

    @PostMapping(AttendanceApiPaths.BOARDINGS)
    public ResponseEntity<AttendanceEventResponse> boarding(@PathVariable Long tripId,
            @Valid @RequestBody BoardingRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        AttendanceEvent event = attendanceEventService.recordBoarding(tripId, request.studentId(), principal);
        return ResponseEntity.ok(attendanceEventMapper.toResponse(event));
    }

    @PostMapping(AttendanceApiPaths.DROPOFFS)
    public ResponseEntity<AttendanceEventResponse> dropoff(@PathVariable Long tripId,
            @Valid @RequestBody DropoffRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        AttendanceEvent event = attendanceEventService.recordDropoff(tripId, request.studentId(), principal);
        return ResponseEntity.ok(attendanceEventMapper.toResponse(event));
    }

    @PostMapping(AttendanceApiPaths.ABSENCES)
    public ResponseEntity<AttendanceEventResponse> absence(@PathVariable Long tripId,
            @Valid @RequestBody AbsenceRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
        AttendanceEvent event = attendanceEventService.recordAbsence(tripId, request.studentId(), request.reason(), principal);
        return ResponseEntity.ok(attendanceEventMapper.toResponse(event));
    }
}
