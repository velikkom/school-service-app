package com.okulservisi.backend.attendance.service.impl;

import com.okulservisi.backend.attendance.dto.response.AbsenceNoticeSummary;
import com.okulservisi.backend.attendance.dto.response.TripRosterEntryResponse;
import com.okulservisi.backend.attendance.entity.AbsenceNoticeStatus;
import com.okulservisi.backend.attendance.entity.AttendanceCorrectionRequest;
import com.okulservisi.backend.attendance.entity.CorrectionStatus;
import com.okulservisi.backend.attendance.entity.TripAbsenceNotice;
import com.okulservisi.backend.attendance.repository.AttendanceCorrectionRequestRepository;
import com.okulservisi.backend.attendance.repository.TripAbsenceNoticeRepository;
import com.okulservisi.backend.attendance.service.AttendanceStatusResolver;
import com.okulservisi.backend.attendance.entity.AttendanceEvent;
import com.okulservisi.backend.attendance.entity.AttendanceEventType;
import com.okulservisi.backend.attendance.repository.AttendanceEventRepository;
import com.okulservisi.backend.attendance.repository.AttendanceEventSpecifications;
import com.okulservisi.backend.attendance.service.AttendanceEventService;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.policy.StudentAccessGuard;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.policy.TripAccessGuard;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "10. Biniş/İniş/Devamsızlık" (bkz. docs/RBAC_MATRIX.md, docs/DATABASE_DESIGN.md "Sefer
 * / Yoklama"). Yetki: ADMIN her zaman; DRIVER/HOSTESS SADECE kendi atandığı sefer için ({@link
 * TripAccessGuard#assertStaffAssigned}, aksi halde 404).
 */
@Service
@RequiredArgsConstructor
public class AttendanceEventServiceImpl implements AttendanceEventService {

    private final AttendanceEventRepository attendanceEventRepository;
    private final TripRepository tripRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TripAccessGuard tripAccessGuard;
    private final StudentAccessGuard studentAccessGuard;
    private final StudentRouteAssignmentRepository studentRouteAssignmentRepository;
    private final TripAbsenceNoticeRepository absenceNoticeRepository;
    private final AttendanceCorrectionRequestRepository correctionRequestRepository;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AttendanceEvent recordBoarding(Long tripId, Long studentId, AuthenticatedUser principal) {
        Trip trip = getTrip(tripId);
        tripAccessGuard.assertStaffAssigned(tripId, principal);
        Student student = getStudent(studentId);

        assertNoAbsenceConflict(tripId, studentId);
        assertNotAlreadyRecorded(tripId, studentId, AttendanceEventType.BOARDING);

        return saveEvent(trip, student, AttendanceEventType.BOARDING, null, principal);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AttendanceEvent recordDropoff(Long tripId, Long studentId, AuthenticatedUser principal) {
        Trip trip = getTrip(tripId);
        tripAccessGuard.assertStaffAssigned(tripId, principal);
        Student student = getStudent(studentId);

        assertNoAbsenceConflict(tripId, studentId);
        assertBoardingExists(tripId, studentId);
        assertNotAlreadyRecorded(tripId, studentId, AttendanceEventType.DROPOFF);

        return saveEvent(trip, student, AttendanceEventType.DROPOFF, null, principal);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AttendanceEvent recordAbsence(Long tripId, Long studentId, String reason, AuthenticatedUser principal) {
        Trip trip = getTrip(tripId);
        tripAccessGuard.assertStaffAssigned(tripId, principal);
        Student student = getStudent(studentId);

        assertNoBoardingOrDropoffConflict(tripId, studentId);
        assertNotAlreadyRecorded(tripId, studentId, AttendanceEventType.ABSENCE);

        return saveEvent(trip, student, AttendanceEventType.ABSENCE, reason, principal);
    }

    @Override
    public List<AttendanceEvent> getStudentAttendance(Long studentId, Instant from, Instant to,
            AuthenticatedUser principal) {
        if (!studentRepository.existsById(studentId)) {
            throw NotFoundException.of("Öğrenci", studentId);
        }
        Specification<AttendanceEvent> scope = switch (principal.getRole()) {
            case ADMIN -> null;
            case DRIVER -> AttendanceEventSpecifications.tripDriverUserIdEquals(principal.getUserId());
            case HOSTESS -> AttendanceEventSpecifications.tripHostessUserIdEquals(principal.getUserId());
            case PARENT, STUDENT -> {
                studentAccessGuard.assertReadable(studentId, principal);
                yield null;
            }
        };
        Specification<AttendanceEvent> spec = Specification.allOf(
                AttendanceEventSpecifications.hasStudentId(studentId),
                AttendanceEventSpecifications.recordedFrom(from),
                AttendanceEventSpecifications.recordedTo(to),
                scope);
        return attendanceEventRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "recordedAt"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripRosterEntryResponse> getRoster(Long tripId, AuthenticatedUser principal) {
        Trip trip = getTrip(tripId);
        tripAccessGuard.assertStaffAssigned(tripId, principal);

        List<StudentRouteAssignment> assignments = studentRouteAssignmentRepository
                .findActiveByRouteAndDirectionAndBusinessDate(
                        trip.getRoute().getId(), trip.getDirection(), trip.getBusinessDate());

        Map<Long, Set<AttendanceEventType>> eventTypesByStudentId = new HashMap<>();
        for (AttendanceEvent event : attendanceEventRepository.findByTripIdAndCorrectionFalse(tripId)) {
            eventTypesByStudentId.computeIfAbsent(event.getStudent().getId(), key -> new HashSet<>())
                    .add(event.getEventType());
        }

        Map<Long, AbsenceNoticeSummary> noticesByStudentId = new HashMap<>();
        for (TripAbsenceNotice notice : absenceNoticeRepository
                .findByTripIdAndStatusOrderByCreatedAtAsc(tripId, AbsenceNoticeStatus.ACTIVE)) {
            noticesByStudentId.put(notice.getStudent().getId(), new AbsenceNoticeSummary(
                    notice.getId(), notice.getNote(), notice.getCreatedAt(), notice.getParent().getFullName()));
        }
        Map<Long, Long> pendingCorrectionByStudentId = new HashMap<>();
        for (AttendanceCorrectionRequest request
                : correctionRequestRepository.findByTripIdAndStatus(tripId, CorrectionStatus.PENDING)) {
            pendingCorrectionByStudentId.put(request.getStudent().getId(), request.getId());
        }

        return assignments.stream()
                .map(assignment -> new TripRosterEntryResponse(
                        assignment.getStudent().getId(),
                        assignment.getStudent().getFullName(),
                        assignment.getStop().getId(),
                        assignment.getStop().getName(),
                        assignment.getStop().getSequenceNo(),
                        assignment.getStop().getPlannedTime(),
                        AttendanceStatusResolver.resolve(eventTypesByStudentId.get(assignment.getStudent().getId())),
                        noticesByStudentId.get(assignment.getStudent().getId()),
                        pendingCorrectionByStudentId.get(assignment.getStudent().getId())))
                .toList();
    }

    private void assertNotAlreadyRecorded(Long tripId, Long studentId, AttendanceEventType type) {
        if (attendanceEventRepository.findByTripIdAndStudentIdAndEventTypeAndCorrectionFalse(tripId, studentId, type)
                .isPresent()) {
            throw new ConflictException("Bu sefer için bu öğrenciye ait " + type + " kaydı zaten var.");
        }
    }

    private void assertBoardingExists(Long tripId, Long studentId) {
        boolean hasBoarding = attendanceEventRepository
                .findByTripIdAndStudentIdAndEventTypeAndCorrectionFalse(tripId, studentId, AttendanceEventType.BOARDING)
                .isPresent();
        if (!hasBoarding) {
            throw new ConflictException("Önce biniş kaydı olmadan iniş kaydedilemez.");
        }
    }

    private void assertNoAbsenceConflict(Long tripId, Long studentId) {
        boolean hasAbsence = attendanceEventRepository
                .findByTripIdAndStudentIdAndEventTypeAndCorrectionFalse(tripId, studentId, AttendanceEventType.ABSENCE)
                .isPresent();
        if (hasAbsence) {
            throw new ConflictException("Bu öğrenci için bu seferde devamsızlık kaydı var, biniş/iniş eklenemez.");
        }
    }

    private void assertNoBoardingOrDropoffConflict(Long tripId, Long studentId) {
        boolean hasBoarding = attendanceEventRepository
                .findByTripIdAndStudentIdAndEventTypeAndCorrectionFalse(tripId, studentId, AttendanceEventType.BOARDING)
                .isPresent();
        boolean hasDropoff = attendanceEventRepository
                .findByTripIdAndStudentIdAndEventTypeAndCorrectionFalse(tripId, studentId, AttendanceEventType.DROPOFF)
                .isPresent();
        if (hasBoarding || hasDropoff) {
            throw new ConflictException("Bu öğrenci için bu seferde biniş/iniş kaydı var, devamsızlık eklenemez.");
        }
    }

    private AttendanceEvent saveEvent(Trip trip, Student student, AttendanceEventType type, String reason,
            AuthenticatedUser principal) {
        AttendanceEvent event = new AttendanceEvent();
        event.setTrip(trip);
        event.setStudent(student);
        event.setEventType(type);
        event.setRecordedAt(Instant.now());
        event.setRecordedBy(userRepository.getReferenceById(principal.getUserId()));
        event.setReason(reason);
        event.setCorrection(false);
        try {
            return attendanceEventRepository.save(event);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Bu sefer+öğrenci+kayıt türü için zaten bir kayıt var.");
        }
    }

    private Trip getTrip(Long tripId) {
        return tripRepository.findById(tripId).orElseThrow(() -> NotFoundException.of("Sefer", tripId));
    }

    private Student getStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new BadRequestException("Belirtilen studentId'ye sahip öğrenci bulunamadı."));
    }
}
