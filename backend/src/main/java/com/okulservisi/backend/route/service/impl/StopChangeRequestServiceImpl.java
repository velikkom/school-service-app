package com.okulservisi.backend.route.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.common.service.AuditLogService;
import com.okulservisi.backend.notification.service.OperationalNotificationService;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.route.dto.request.StopChangeRequestCreateRequest;
import com.okulservisi.backend.route.dto.response.StopChangeRequestResponse;
import com.okulservisi.backend.route.dto.response.StopOptionResponse;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.Stop;
import com.okulservisi.backend.route.entity.StopChangeRequest;
import com.okulservisi.backend.route.entity.StopChangeRequestStatus;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.route.repository.StopChangeRequestRepository;
import com.okulservisi.backend.route.repository.StopRepository;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.route.service.StopChangeRequestService;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.policy.StudentScopePolicy;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.trip.policy.TripAudienceResolver;
import com.okulservisi.backend.trip.service.TripLabels;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md §25. Onayda ADMIN adına backend doğrular: (1) durak AKTİF bir güzergâhta ve yönü talep
 * yönüyle aynı, (2) yürürlük tarihi bugün veya sonrası (Europe/Istanbul), (3) öğrencinin o yön için
 * çakışan başka ataması yok. Mevcut atama yürürlük tarihinden bir gün önce kapatılır, yeni atama
 * yürürlükten itibaren (eski bitiş tarihine kadar) açılır; mevcut atama aynı gün başlıyorsa sadece
 * güzergâh/durak güncellenir — böylece aynı tarihte iki aktif atama asla oluşmaz.
 * {@code AssignmentServiceImpl} ile AYNI SERIALIZABLE deseni; talep satırı {@code FOR UPDATE}
 * ile kilitlendiği için aynı talep iki kez sonuçlandırılamaz.
 */
@Service
@RequiredArgsConstructor
public class StopChangeRequestServiceImpl implements StopChangeRequestService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final long NO_EXCLUSION = -1L;

    private final StopChangeRequestRepository requestRepository;
    private final StopRepository stopRepository;
    private final StudentRouteAssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final StudentScopePolicy studentScopePolicy;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final TripAudienceResolver audienceResolver;
    private final OperationalNotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<StopOptionResponse> options(Long studentId, RouteDirection direction, AuthenticatedUser principal) {
        if (!studentScopePolicy.canAccess(principal, studentId) || !studentRepository.existsById(studentId)) {
            throw NotFoundException.of("Öğrenci", studentId);
        }
        return stopRepository.findOnActiveRoutesByDirection(direction).stream()
                .map(stop -> new StopOptionResponse(stop.getId(), stop.getName(), stop.getPlannedTime(),
                        stop.getSequenceNo(), stop.getRoute().getId(), stop.getRoute().getName()))
                .toList();
    }

    @Override
    @Transactional
    public StopChangeRequestResponse create(StopChangeRequestCreateRequest request, AuthenticatedUser principal) {
        Parent parent = parentRepository.findByUserId(principal.getUserId())
                .orElseThrow(() -> new NotFoundException("Veli profili bulunamadı."));
        Long studentId = request.studentId();
        if (!studentScopePolicy.canAccess(principal, studentId)) {
            throw NotFoundException.of("Öğrenci", studentId);
        }
        Stop stop = stopRepository.findWithRouteById(request.requestedStopId())
                .orElseThrow(() -> new BadRequestException("Seçilen durak bulunamadı."));
        assertStopSuitable(stop, request.direction(), BadRequestException::new);
        if (request.effectiveDate().isBefore(today())) {
            throw new BadRequestException("Yürürlük tarihi bugünden önce olamaz.");
        }

        // Aynı öğrenci için eşzamanlı talepleri serileştirir (tek bekleyen talep kuralı).
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> NotFoundException.of("Öğrenci", studentId));
        if (!student.isActive()) {
            throw new ConflictException("Pasif öğrenci için durak değişikliği talebi açılamaz.");
        }
        if (requestRepository.existsByStudentIdAndDirectionAndStatus(studentId, request.direction(),
                StopChangeRequestStatus.PENDING)) {
            throw new ConflictException("Bu öğrenci ve yön için zaten bekleyen bir durak değişikliği talebi var.");
        }

        StopChangeRequest entity = new StopChangeRequest();
        entity.setStudent(student);
        entity.setParent(parent);
        entity.setRequestedBy(userRepository.getReferenceById(principal.getUserId()));
        entity.setDirection(request.direction());
        entity.setRequestedStop(stop);
        entity.setEffectiveDate(request.effectiveDate());
        entity.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        entity.setStatus(StopChangeRequestStatus.PENDING);
        StopChangeRequest saved = requestRepository.saveAndFlush(entity);
        entityManager.refresh(saved);

        notificationService.notify(audienceResolver.adminUsers(), "Yeni durak değişikliği talebi",
                student.getFullName() + " için " + TripLabels.direction(request.direction()) + " durağı "
                        + stop.getName() + " (" + stop.getRoute().getName() + ") olarak talep edildi, yürürlük "
                        + TripLabels.date(request.effectiveDate()) + ".",
                "stop-change-request:" + saved.getId() + ":created");
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StopChangeRequestResponse> list(StopChangeRequestStatus status, AuthenticatedUser principal,
            Pageable pageable) {
        Specification<StopChangeRequest> spec = Specification.allOf(
                (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status),
                (root, query, cb) -> principal.getRole() == Role.ADMIN ? null
                        : cb.equal(root.get("requestedBy").get("id"), principal.getUserId()));
        return requestRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public StopChangeRequestResponse approve(Long id, String note, AuthenticatedUser principal) {
        StopChangeRequest request = lockPending(id);
        Stop stop = stopRepository.findWithRouteById(request.getRequestedStop().getId())
                .orElseThrow(() -> new ConflictException("Talep edilen durak artık mevcut değil."));
        assertStopSuitable(stop, request.getDirection(), ConflictException::new);
        LocalDate effective = request.getEffectiveDate();
        if (effective.isBefore(today())) {
            throw new ConflictException("Talebin yürürlük tarihi geçmiş; talebi reddedip veliden yeni talep isteyin.");
        }
        Long studentId = request.getStudent().getId();
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> NotFoundException.of("Öğrenci", studentId));

        List<StudentRouteAssignment> current =
                assignmentRepository.findActiveForStudentOnDate(studentId, request.getDirection(), effective);
        if (current.size() > 1) {
            throw new ConflictException("Öğrencinin bu yön ve tarih için birden fazla aktif ataması var; önce atamaları düzeltin.");
        }

        StudentRouteAssignment resulting;
        String previousStopName = null;
        if (current.isEmpty()) {
            if (!assignmentRepository.findOverlapping(studentId, request.getDirection(), effective, null, NO_EXCLUSION)
                    .isEmpty()) {
                throw new ConflictException(
                        "Öğrencinin yürürlük tarihinden sonra başlayan başka bir ataması var (atama çakışması).");
            }
            resulting = newAssignment(student, stop, request.getDirection(), effective, null);
        } else {
            StudentRouteAssignment existing = current.get(0);
            previousStopName = existing.getStop().getName();
            if (existing.getStop().getId().equals(stop.getId())) {
                throw new ConflictException("Öğrenci bu tarihte zaten talep edilen durağa atanmış.");
            }
            LocalDate oldValidTo = existing.getValidTo();
            if (!assignmentRepository.findOverlapping(studentId, request.getDirection(), effective, oldValidTo,
                    existing.getId()).isEmpty()) {
                throw new ConflictException("Yürürlük döneminde öğrencinin başka bir ataması var (atama çakışması).");
            }
            if (!existing.getValidFrom().isBefore(effective)) {
                existing.setRoute(stop.getRoute());
                existing.setStop(stop);
                resulting = assignmentRepository.save(existing);
            } else {
                existing.setValidTo(effective.minusDays(1));
                assignmentRepository.saveAndFlush(existing);
                resulting = newAssignment(student, stop, request.getDirection(), effective, oldValidTo);
            }
        }

        Instant now = Instant.now();
        request.setStatus(StopChangeRequestStatus.APPROVED);
        request.setResultingAssignment(resulting);
        request.setDecidedBy(userRepository.getReferenceById(principal.getUserId()));
        request.setDecidedAt(now);
        request.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
        StopChangeRequest saved = requestRepository.saveAndFlush(request);
        entityManager.refresh(saved);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("studentId", studentId);
        detail.put("direction", request.getDirection().name());
        detail.put("previousStop", previousStopName);
        detail.put("newStopId", stop.getId());
        detail.put("effectiveDate", effective.toString());
        detail.put("resultingAssignmentId", resulting.getId());
        auditLogService.record(principal.getUserId(), "STOP_CHANGE_APPROVED", "stop_change_request", saved.getId(), detail);

        notifyFamily(saved, "Durak değişikliği talebiniz onaylandı",
                student.getFullName() + " için " + TripLabels.direction(request.getDirection()) + " durağı "
                        + TripLabels.date(effective) + " tarihinden itibaren " + stop.getName() + " ("
                        + stop.getRoute().getName() + ", planlanan saat " + TripLabels.time(stop.getPlannedTime())
                        + ") olarak güncellendi." + (saved.getDecisionNote() == null ? "" : " Not: " + saved.getDecisionNote()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public StopChangeRequestResponse reject(Long id, String reason, AuthenticatedUser principal) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Ret gerekçesi zorunludur.");
        }
        StopChangeRequest request = lockPending(id);
        request.setStatus(StopChangeRequestStatus.REJECTED);
        request.setDecidedBy(userRepository.getReferenceById(principal.getUserId()));
        request.setDecidedAt(Instant.now());
        request.setDecisionNote(reason.trim());
        StopChangeRequest saved = requestRepository.saveAndFlush(request);
        entityManager.refresh(saved);
        notifyFamily(saved, "Durak değişikliği talebiniz reddedildi",
                saved.getStudent().getFullName() + " için " + TripLabels.direction(saved.getDirection())
                        + " durak değişikliği talebiniz reddedildi. Gerekçe: " + saved.getDecisionNote());
        return toResponse(saved);
    }

    private StopChangeRequest lockPending(Long id) {
        StopChangeRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.of("Durak değişikliği talebi", id));
        entityManager.refresh(request);
        if (request.getStatus() != StopChangeRequestStatus.PENDING) {
            throw new ConflictException("Bu talep zaten sonuçlandırılmış (" + request.getStatus() + ").");
        }
        return request;
    }

    private StudentRouteAssignment newAssignment(Student student, Stop stop, RouteDirection direction,
            LocalDate validFrom, LocalDate validTo) {
        StudentRouteAssignment assignment = new StudentRouteAssignment();
        assignment.setStudent(student);
        assignment.setRoute(stop.getRoute());
        assignment.setStop(stop);
        assignment.setDirection(direction);
        assignment.setValidFrom(validFrom);
        assignment.setValidTo(validTo);
        return assignmentRepository.saveAndFlush(assignment);
    }

    private void assertStopSuitable(Stop stop, RouteDirection direction,
            java.util.function.Function<String, ? extends RuntimeException> error) {
        Route route = stop.getRoute();
        if (!route.isActive()) {
            throw error.apply("Seçilen durağın güzergâhı aktif değil.");
        }
        if (route.getDirection() != direction) {
            throw error.apply("Seçilen durak bu yöndeki (" + TripLabels.direction(direction) + ") bir güzergâha ait değil.");
        }
    }

    private void notifyFamily(StopChangeRequest request, String title, String body) {
        List<User> recipients = new java.util.ArrayList<>(audienceResolver.familyUsersOfStudent(request.getStudent()));
        recipients.add(request.getRequestedBy());
        notificationService.notify(recipients, title, body, "stop-change-request:" + request.getId() + ":decided");
    }

    private LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private StopChangeRequestResponse toResponse(StopChangeRequest request) {
        Stop stop = request.getRequestedStop();
        List<StudentRouteAssignment> current = assignmentRepository.findActiveForStudentOnDate(
                request.getStudent().getId(), request.getDirection(), today());
        return new StopChangeRequestResponse(
                request.getId(),
                request.getStudent().getId(),
                request.getStudent().getFullName(),
                request.getDirection(),
                current.isEmpty() ? null : current.get(0).getStop().getName(),
                stop.getId(),
                stop.getName(),
                stop.getRoute().getName(),
                request.getEffectiveDate(),
                request.getReason(),
                request.getStatus(),
                request.getDecisionNote(),
                request.getDecidedAt(),
                request.getCreatedAt(),
                request.getRequestedBy().getFullName(),
                request.getResultingAssignment() == null ? null : request.getResultingAssignment().getId());
    }
}
