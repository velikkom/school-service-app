package com.okulservisi.backend.trip.service;

import com.okulservisi.backend.attendance.dto.response.AbsenceNoticeSummary;
import com.okulservisi.backend.attendance.dto.response.RosterStatus;
import com.okulservisi.backend.attendance.entity.AbsenceNoticeStatus;
import com.okulservisi.backend.attendance.entity.TripAbsenceNotice;
import com.okulservisi.backend.attendance.repository.TripAbsenceNoticeRepository;
import com.okulservisi.backend.attendance.service.AttendanceStatusResolver;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.student.policy.StudentAccessGuard;
import com.okulservisi.backend.trip.dto.response.DailyServiceEntryResponse;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.entity.TripIncidentType;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.trip.repository.TripIncidentRepository;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.user.entity.Role;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md §26 — "Bugünkü Servisim" (STUDENT kendisi; PARENT bağlı çocuğu; ADMIN). Kapsam
 * {@link StudentAccessGuard} ile doğrulanır (kapsam dışı 404). Sadece okuma.
 */
@Service
@RequiredArgsConstructor
public class DailyServiceQueryService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final StudentAccessGuard studentAccessGuard;
    private final StudentRouteAssignmentRepository assignmentRepository;
    private final TripRepository tripRepository;
    private final TripAbsenceNoticeRepository absenceNoticeRepository;
    private final TripIncidentRepository incidentRepository;
    private final AttendanceStatusResolver statusResolver;

    @Transactional(readOnly = true)
    public List<DailyServiceEntryResponse> getDailyService(Long studentId, LocalDate date, AuthenticatedUser principal) {
        studentAccessGuard.assertReadable(studentId, principal);
        LocalDate businessDate = date != null ? date : LocalDate.now(ZONE);
        boolean parent = principal.getRole() == Role.PARENT;

        List<DailyServiceEntryResponse> entries = new ArrayList<>();
        for (RouteDirection direction : List.of(RouteDirection.MORNING, RouteDirection.AFTERNOON)) {
            for (StudentRouteAssignment assignment
                    : assignmentRepository.findActiveForStudentOnDate(studentId, direction, businessDate)) {
                for (Trip trip : tripRepository.findByRouteIdAndDirectionAndBusinessDate(
                        assignment.getRoute().getId(), direction, businessDate)) {
                    entries.add(toEntry(trip, assignment, studentId, parent));
                }
            }
        }
        return entries;
    }

    private DailyServiceEntryResponse toEntry(Trip trip, StudentRouteAssignment assignment, Long studentId,
            boolean parent) {
        RosterStatus attendance = statusResolver.currentStatus(trip.getId(), studentId);
        TripAbsenceNotice notice = absenceNoticeRepository
                .findByTripIdAndStudentIdAndStatus(trip.getId(), studentId, AbsenceNoticeStatus.ACTIVE).orElse(null);
        boolean open = trip.getStatus() == TripStatus.PLANNED || trip.getStatus() == TripStatus.IN_PROGRESS;
        boolean noAttendance = attendance == RosterStatus.NONE;
        DailyServiceEntryResponse.IncidentSummary incident = incidentRepository
                .findFirstByTripIdAndTypeNotOrderByCreatedAtDescIdDesc(trip.getId(), TripIncidentType.CANCELLATION_REQUEST)
                .map(i -> new DailyServiceEntryResponse.IncidentSummary(i.getType(), i.getDelayMinutes(), i.getNote(),
                        i.getCreatedAt()))
                .orElse(null);
        return new DailyServiceEntryResponse(
                trip.getId(),
                trip.getDirection(),
                trip.getBusinessDate(),
                trip.getStatus(),
                trip.getPlannedStartTime(),
                trip.getRoute().getName(),
                trip.getVehicle().getPlate(),
                assignment.getStop().getId(),
                assignment.getStop().getName(),
                assignment.getStop().getPlannedTime(),
                attendance,
                notice == null ? null : new AbsenceNoticeSummary(notice.getId(), notice.getNote(), notice.getCreatedAt(),
                        notice.getParent().getFullName()),
                parent && notice == null && open && noAttendance,
                parent && notice != null && open && noAttendance,
                incident,
                trip.getCancellationReason());
    }
}
