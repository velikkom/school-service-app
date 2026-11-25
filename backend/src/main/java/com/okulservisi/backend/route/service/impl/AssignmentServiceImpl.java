package com.okulservisi.backend.route.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.route.dto.request.AssignmentCreateRequest;
import com.okulservisi.backend.route.dto.request.AssignmentUpdateRequest;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.Stop;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.route.repository.AssignmentSpecifications;
import com.okulservisi.backend.route.repository.RouteRepository;
import com.okulservisi.backend.route.repository.StopRepository;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.route.service.AssignmentService;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.policy.StudentAccessGuard;
import com.okulservisi.backend.student.policy.StudentScopePolicy;
import com.okulservisi.backend.student.repository.StudentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.okulservisi.backend.notification.service.OperationalNotificationService;
import com.okulservisi.backend.trip.policy.TripAudienceResolver;
import com.okulservisi.backend.trip.service.TripLabels;

/**
 * docs/API.md "8. Öğrenci atamaları" — ADMIN create/update; PARENT/STUDENT kendi kapsamında
 * salt-okunur. Çakışma kuralı (docs/DATABASE_DESIGN.md): aynı öğrenci+yön için tarih aralığı çakışan
 * ikinci atama, {@code SERIALIZABLE} işlem içinde gerçek bir overlap sorgusuyla ({@code
 * StudentRouteAssignmentRepository#findOverlapping}) engellenir — yorum değil, çalışan kontrol.
 */
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final OperationalNotificationService notificationService;
    private final TripAudienceResolver audienceResolver;

    /** Henüz var olmayan bir kaydı "hariç tutmak" için kullanılan, hiçbir gerçek ID ile çakışmayan değer. */
    private static final Long NO_EXCLUSION = 0L;

    private final StudentRouteAssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final StudentScopePolicy studentScopePolicy;

    @Override
    public Page<StudentRouteAssignment> list(Long studentId, Long routeId, RouteDirection direction,
            AuthenticatedUser principal, Pageable pageable) {
        Specification<StudentRouteAssignment> spec = Specification.allOf(
                AssignmentSpecifications.hasStudentId(studentId),
                AssignmentSpecifications.hasRouteId(routeId),
                AssignmentSpecifications.hasDirection(direction),
                scopeSpecification(principal));
        return assignmentRepository.findAll(spec, pageable);
    }

    private Specification<StudentRouteAssignment> scopeSpecification(AuthenticatedUser principal) {
        Set<Long> ids = studentScopePolicy.accessibleStudentIds(principal);
        return ids == null ? null : AssignmentSpecifications.studentIdIn(ids);
    }

    @Override
    public StudentRouteAssignment getByIdForPrincipal(Long id, AuthenticatedUser principal) {
        StudentRouteAssignment assignment = getById(id);
        studentAccessGuard.assertReadable(assignment.getStudent().getId(), principal);
        return assignment;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public StudentRouteAssignment create(AssignmentCreateRequest request) {
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new BadRequestException("Belirtilen studentId'ye sahip öğrenci bulunamadı."));
        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new BadRequestException("Belirtilen routeId'ye sahip güzergâh bulunamadı."));
        Stop stop = stopRepository.findByIdAndRouteId(request.stopId(), route.getId())
                .orElseThrow(() -> new BadRequestException("Belirtilen stopId bu güzergâha ait değil."));
        assertDirectionConsistent(route, request.direction());
        assertValidDateRange(request.validFrom(), request.validTo());

        assertNoOverlap(student.getId(), request.direction(), request.validFrom(), request.validTo(), NO_EXCLUSION);

        StudentRouteAssignment assignment = new StudentRouteAssignment();
        assignment.setStudent(student);
        assignment.setRoute(route);
        assignment.setStop(stop);
        assignment.setDirection(request.direction());
        assignment.setValidFrom(request.validFrom());
        assignment.setValidTo(request.validTo());
        return assignmentRepository.save(assignment);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public StudentRouteAssignment update(Long id, AssignmentUpdateRequest request) {
        StudentRouteAssignment assignment = getById(id);
        Long previousStopId = assignment.getStop().getId();
        String previousStopName = assignment.getStop().getName();

        Route route = assignment.getRoute();
        if (request.routeId() != null && !request.routeId().equals(route.getId())) {
            route = routeRepository.findById(request.routeId())
                    .orElseThrow(() -> new BadRequestException("Belirtilen routeId'ye sahip güzergâh bulunamadı."));
            assignment.setRoute(route);
        }

        if (request.stopId() != null && !request.stopId().equals(assignment.getStop().getId())) {
            Stop stop = stopRepository.findByIdAndRouteId(request.stopId(), route.getId())
                    .orElseThrow(() -> new BadRequestException("Belirtilen stopId bu güzergâha ait değil."));
            assignment.setStop(stop);
        } else if (request.routeId() != null && !assignment.getStop().getRoute().getId().equals(route.getId())) {
            throw new BadRequestException("Güzergâh değiştirildiğinde geçerli bir stopId belirtilmelidir.");
        }

        RouteDirection direction = request.direction() != null ? request.direction() : assignment.getDirection();
        LocalDate validFrom = request.validFrom() != null ? request.validFrom() : assignment.getValidFrom();
        LocalDate validTo = request.validTo() != null ? request.validTo() : assignment.getValidTo();

        assertDirectionConsistent(route, direction);
        assertValidDateRange(validFrom, validTo);
        assertNoOverlap(assignment.getStudent().getId(), direction, validFrom, validTo, assignment.getId());

        assignment.setDirection(direction);
        assignment.setValidFrom(validFrom);
        assignment.setValidTo(validTo);
        StudentRouteAssignment saved = assignmentRepository.save(assignment);

        // 2026-11-18 (docs/API.md §21): seferi etkileyen durak değişikliği → veli + öğrenciye bildirim
        // (aynı transaction; aynı değişiklik tekrar işlenirse event_key sayesinde çoğalmaz).
        if (!previousStopId.equals(saved.getStop().getId())) {
            notificationService.notify(audienceResolver.familyUsersOfStudent(saved.getStudent()),
                    "Servis durağı değişti",
                    saved.getStudent().getFullName() + " için " + TripLabels.direction(saved.getDirection())
                            + " durağı " + previousStopName + " yerine " + saved.getStop().getName()
                            + " (planlanan saat " + TripLabels.time(saved.getStop().getPlannedTime()) + ") olarak güncellendi.",
                    "assignment:" + saved.getId() + ":stop:" + previousStopId + "-" + saved.getStop().getId()
                            + ":" + saved.getValidFrom());
        }
        return saved;
    }

    private void assertNoOverlap(Long studentId, RouteDirection direction, LocalDate validFrom, LocalDate validTo,
            Long excludingId) {
        List<StudentRouteAssignment> overlapping =
                assignmentRepository.findOverlapping(studentId, direction, validFrom, validTo, excludingId);
        if (!overlapping.isEmpty()) {
            throw new ConflictException(
                    "Bu öğrenci için aynı yönde, tarih aralığı çakışan başka bir aktif atama zaten var.");
        }
    }

    private void assertDirectionConsistent(Route route, RouteDirection direction) {
        if (route.getDirection() != direction) {
            throw new BadRequestException("Atamanın yönü (direction), güzergâhın yönüyle eşleşmelidir.");
        }
    }

    private void assertValidDateRange(LocalDate validFrom, LocalDate validTo) {
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new BadRequestException("validTo, validFrom'dan önce olamaz.");
        }
    }

    private StudentRouteAssignment getById(Long id) {
        return assignmentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Öğrenci ataması", id));
    }
}
