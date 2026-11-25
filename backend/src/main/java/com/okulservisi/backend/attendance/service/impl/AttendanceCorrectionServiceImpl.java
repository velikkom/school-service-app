package com.okulservisi.backend.attendance.service.impl;

import com.okulservisi.backend.attendance.dto.request.AttendanceCorrectionApproveRequest;
import com.okulservisi.backend.attendance.dto.request.AttendanceCorrectionCreateRequest;
import com.okulservisi.backend.attendance.dto.response.AttendanceCorrectionResponse;
import com.okulservisi.backend.attendance.dto.response.RosterStatus;
import com.okulservisi.backend.attendance.entity.AttendanceCorrectionRequest;
import com.okulservisi.backend.attendance.entity.AttendanceEvent;
import com.okulservisi.backend.attendance.entity.AttendanceEventType;
import com.okulservisi.backend.attendance.entity.CorrectionKind;
import com.okulservisi.backend.attendance.entity.CorrectionStatus;
import com.okulservisi.backend.attendance.repository.AttendanceCorrectionRequestRepository;
import com.okulservisi.backend.attendance.repository.AttendanceEventRepository;
import com.okulservisi.backend.attendance.service.AttendanceCorrectionService;
import com.okulservisi.backend.attendance.service.AttendanceStatusResolver;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.common.service.AuditLogService;
import com.okulservisi.backend.notification.service.OperationalNotificationService;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.policy.StudentScopePolicy;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.policy.TripAccessGuard;
import com.okulservisi.backend.trip.policy.TripAudienceResolver;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.trip.service.TripLabels;
import com.okulservisi.backend.trip.service.TripService;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md §24. Kurallar:
 * <ul>
 *   <li><b>Kapsam:</b> DRIVER/HOSTESS sadece atandığı sefer ({@link TripAccessGuard}); PARENT sadece
 *       aktif bağlı çocuğu ve çocuğun yolcusu olduğu sefer; listeleme/detayda ADMIN dışı roller sadece
 *       KENDİ açtığı talepleri görür (kapsam dışı 404).</li>
 *   <li><b>Eski veri koruması:</b> {@code sourceStatus} talep anındaki durumdur; onay anında güncel durum
 *       farklıysa 409 — talep verisiyle üzerine yazılmaz.</li>
 *   <li><b>Çift sonuçlandırma:</b> talep satırı {@code FOR UPDATE} ile kilitlenir ve PENDING değilse
 *       409; ikinci onay hiçbir yoklama satırına dokunmaz.</li>
 *   <li><b>Biniş/iniş kuralları:</b> hedef durum geçerli bir olay kümesine çevrilir (DROPPED_OFF ⇒
 *       BINIŞ+İNİŞ, ABSENT ⇒ sadece DEVAMSIZLIK); fazlalık kayıtlar SİLİNMEZ, geçersiz (voided) işaretlenir,
 *       eksikler eklenir. Böylece iniş biniş olmadan, devamsızlık biniş/iniş ile birlikte kalamaz ve
 *       tekrar kayıt oluşmaz (kısmi benzersiz indeks).</li>
 *   <li><b>Denetim:</b> eski/yeni değer, gerekçe, talep eden, karar veren ve zaman {@code audit_log}'a yazılır.</li>
 * </ul>
 * Kilit sırası diğer sefer işlemleriyle aynı: önce sefer satırı, sonra talep satırı.
 */
@Service
@RequiredArgsConstructor
public class AttendanceCorrectionServiceImpl implements AttendanceCorrectionService {

    private static final String ENTITY = "attendance_correction_request";

    private final AttendanceCorrectionRequestRepository requestRepository;
    private final AttendanceEventRepository attendanceEventRepository;
    private final AttendanceStatusResolver statusResolver;
    private final TripRepository tripRepository;
    private final TripService tripService;
    private final TripAccessGuard tripAccessGuard;
    private final TripAudienceResolver audienceResolver;
    private final StudentRepository studentRepository;
    private final StudentScopePolicy studentScopePolicy;
    private final StudentRouteAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final OperationalNotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public AttendanceCorrectionResponse create(AttendanceCorrectionCreateRequest request, AuthenticatedUser principal) {
        Long tripId = request.tripId();
        Long studentId = request.studentId();
        CorrectionKind kind;
        switch (principal.getRole()) {
            case DRIVER, HOSTESS -> {
                tripAccessGuard.assertStaffAssigned(tripId, principal);
                if (request.requestedStatus() == null) {
                    throw new BadRequestException("Düzeltme talebinde istenen durum (requestedStatus) zorunludur.");
                }
                kind = CorrectionKind.CORRECTION;
            }
            case PARENT -> {
                if (!studentScopePolicy.canAccess(principal, studentId)) {
                    throw NotFoundException.of("Öğrenci", studentId);
                }
                kind = CorrectionKind.REVIEW;
            }
            default -> throw NotFoundException.of("Sefer", tripId);
        }
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> NotFoundException.of("Sefer", tripId));
        // Veli sadece çocuğunun yolcusu olduğu sefer için talep açabilir (kapsam dışı 404).
        if (kind == CorrectionKind.REVIEW && !assignmentRepository.isOnRoster(studentId, trip.getRoute().getId(),
                trip.getDirection(), trip.getBusinessDate())) {
            throw NotFoundException.of("Sefer", tripId);
        }
        // Aynı öğrenci için eşzamanlı talepleri serileştirir (tek bekleyen talep kuralı).
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> NotFoundException.of("Öğrenci", studentId));

        RosterStatus current = statusResolver.currentStatus(tripId, studentId);
        if (current == RosterStatus.NONE) {
            throw new ConflictException("Bu öğrenci için bu seferde düzeltilecek bir yoklama kaydı yok.");
        }
        if (request.requestedStatus() != null && request.requestedStatus() == current) {
            throw new BadRequestException("İstenen durum mevcut yoklama durumuyla aynı.");
        }
        if (requestRepository.existsByTripIdAndStudentIdAndStatus(tripId, studentId, CorrectionStatus.PENDING)) {
            throw new ConflictException("Bu öğrenci ve sefer için zaten bekleyen bir düzeltme talebi var.");
        }

        User requester = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı."));
        AttendanceCorrectionRequest entity = new AttendanceCorrectionRequest();
        entity.setTrip(trip);
        entity.setStudent(student);
        entity.setKind(kind);
        entity.setSourceStatus(current);
        entity.setRequestedStatus(request.requestedStatus());
        entity.setReason(request.reason().trim());
        entity.setStatus(CorrectionStatus.PENDING);
        entity.setRequestedBy(requester);
        AttendanceCorrectionRequest saved = requestRepository.saveAndFlush(entity);
        entityManager.refresh(saved);

        notificationService.notify(audienceResolver.adminUsers(), "Yeni yoklama düzeltme talebi",
                student.getFullName() + " — " + TripLabels.describe(trip) + ": " + saved.getReason()
                        + " (" + requester.getFullName() + ")",
                "attendance-correction:" + saved.getId() + ":created");
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AttendanceCorrectionResponse> list(CorrectionStatus status, Long tripId, AuthenticatedUser principal,
            Pageable pageable) {
        Specification<AttendanceCorrectionRequest> spec = Specification.allOf(
                (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status),
                (root, query, cb) -> tripId == null ? null : cb.equal(root.get("trip").get("id"), tripId),
                (root, query, cb) -> principal.getRole() == Role.ADMIN ? null
                        : cb.equal(root.get("requestedBy").get("id"), principal.getUserId()));
        return requestRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceCorrectionResponse get(Long id, AuthenticatedUser principal) {
        AttendanceCorrectionRequest request = requestRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Düzeltme talebi", id));
        if (principal.getRole() != Role.ADMIN && !request.getRequestedBy().getId().equals(principal.getUserId())) {
            throw NotFoundException.of("Düzeltme talebi", id);
        }
        return toResponse(request);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AttendanceCorrectionResponse approve(Long id, AttendanceCorrectionApproveRequest body,
            AuthenticatedUser principal) {
        AttendanceCorrectionRequest request = lockForDecision(id);
        RosterStatus target = body.newStatus() != null ? body.newStatus() : request.getRequestedStatus();
        if (target == null) {
            throw new BadRequestException("Onay için yeni durum (newStatus) belirtilmelidir.");
        }
        Long tripId = request.getTrip().getId();
        Long studentId = request.getStudent().getId();
        RosterStatus current = statusResolver.currentStatus(tripId, studentId);
        if (current != request.getSourceStatus()) {
            throw new ConflictException("Talep açıldıktan sonra yoklama kaydı değişti (talep anında: "
                    + request.getSourceStatus() + ", güncel: " + current
                    + "). Eski talep verisiyle üzerine yazılmadı; güncel duruma göre talebi reddedin.");
        }
        if (target == current) {
            throw new ConflictException("Yeni durum mevcut yoklama durumuyla aynı; uygulanacak düzeltme yok.");
        }

        Set<AttendanceEventType> targetTypes = AttendanceStatusResolver.eventTypesFor(target);
        List<AttendanceEvent> existing =
                attendanceEventRepository.findByTripIdAndStudentIdAndCorrectionFalse(tripId, studentId);
        List<Map<String, Object>> voided = new ArrayList<>();
        Set<AttendanceEventType> keptTypes = EnumSet.noneOf(AttendanceEventType.class);
        for (AttendanceEvent event : existing) {
            if (targetTypes.contains(event.getEventType()) && !keptTypes.contains(event.getEventType())) {
                keptTypes.add(event.getEventType());
                continue;
            }
            attendanceEventRepository.voidEvent(event.getId(), request.getId());
            voided.add(eventDetail(event));
        }
        User admin = userRepository.getReferenceById(principal.getUserId());
        List<Long> createdIds = new ArrayList<>();
        Instant now = Instant.now();
        // BINIŞ her zaman İNİŞ'ten önce eklenir (iniş-biniş sırası kuralı).
        for (AttendanceEventType type : List.of(AttendanceEventType.BOARDING, AttendanceEventType.DROPOFF,
                AttendanceEventType.ABSENCE)) {
            if (targetTypes.contains(type) && !keptTypes.contains(type)) {
                AttendanceEvent event = new AttendanceEvent();
                event.setTrip(request.getTrip());
                event.setStudent(request.getStudent());
                event.setEventType(type);
                event.setRecordedAt(now);
                event.setRecordedBy(admin);
                event.setReason("Yoklama düzeltmesi #" + request.getId());
                event.setCorrection(false);
                event.setCreatedByCorrectionId(request.getId());
                createdIds.add(attendanceEventRepository.saveAndFlush(event).getId());
            }
        }

        RosterStatus applied = statusResolver.currentStatus(tripId, studentId);
        if (applied != target) {
            throw new IllegalStateException("Düzeltme sonrası durum beklenenle eşleşmiyor.");
        }

        request.setStatus(CorrectionStatus.APPROVED);
        request.setAppliedStatus(target);
        request.setDecidedBy(admin);
        request.setDecidedAt(now);
        request.setDecisionNote(body.note() == null || body.note().isBlank() ? null : body.note().trim());
        AttendanceCorrectionRequest saved = requestRepository.saveAndFlush(request);
        entityManager.refresh(saved);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tripId", tripId);
        detail.put("studentId", studentId);
        detail.put("oldStatus", current.name());
        detail.put("newStatus", target.name());
        detail.put("requestReason", saved.getReason());
        detail.put("decisionNote", saved.getDecisionNote());
        detail.put("requestedByUserId", saved.getRequestedBy().getId());
        detail.put("requestedByFullName", saved.getRequestedBy().getFullName());
        detail.put("requestedAt", String.valueOf(saved.getCreatedAt()));
        detail.put("decidedByUserId", principal.getUserId());
        detail.put("decidedAt", now.toString());
        detail.put("voidedEvents", voided);
        detail.put("createdEventIds", createdIds);
        auditLogService.record(principal.getUserId(), "ATTENDANCE_CORRECTION_APPROVED", ENTITY, saved.getId(), detail);

        notifyRequester(saved, "Yoklama düzeltme talebiniz onaylandı",
                "yeni durum: " + label(target) + (saved.getDecisionNote() == null ? "" : ". Not: " + saved.getDecisionNote()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AttendanceCorrectionResponse reject(Long id, String reason, AuthenticatedUser principal) {
        AttendanceCorrectionRequest request = lockForDecision(id);
        Instant now = Instant.now();
        request.setStatus(CorrectionStatus.REJECTED);
        request.setDecidedBy(userRepository.getReferenceById(principal.getUserId()));
        request.setDecidedAt(now);
        request.setDecisionNote(reason.trim());
        AttendanceCorrectionRequest saved = requestRepository.saveAndFlush(request);
        entityManager.refresh(saved);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tripId", saved.getTrip().getId());
        detail.put("studentId", saved.getStudent().getId());
        detail.put("requestReason", saved.getReason());
        detail.put("decisionNote", saved.getDecisionNote());
        detail.put("requestedByUserId", saved.getRequestedBy().getId());
        detail.put("decidedByUserId", principal.getUserId());
        detail.put("decidedAt", now.toString());
        auditLogService.record(principal.getUserId(), "ATTENDANCE_CORRECTION_REJECTED", ENTITY, saved.getId(), detail);

        notifyRequester(saved, "Yoklama düzeltme talebiniz reddedildi", "gerekçe: " + saved.getDecisionNote());
        return toResponse(saved);
    }

    /** Kilit sırası: sefer satırı → talep satırı. PENDING değilse 409 (çift sonuçlandırma yok). */
    private AttendanceCorrectionRequest lockForDecision(Long id) {
        AttendanceCorrectionRequest unlocked = requestRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Düzeltme talebi", id));
        tripService.lockById(unlocked.getTrip().getId());
        AttendanceCorrectionRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.of("Düzeltme talebi", id));
        entityManager.refresh(request);
        if (request.getStatus() != CorrectionStatus.PENDING) {
            throw new ConflictException("Bu talep zaten sonuçlandırılmış (" + request.getStatus() + ").");
        }
        return request;
    }

    private void notifyRequester(AttendanceCorrectionRequest request, String title, String suffix) {
        String body = request.getStudent().getFullName() + " — " + TripLabels.describe(request.getTrip())
                + " için talebiniz sonuçlandı: " + suffix;
        notificationService.notify(List.of(request.getRequestedBy()), title, body,
                "attendance-correction:" + request.getId() + ":decided");
    }

    private Map<String, Object> eventDetail(AttendanceEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("type", event.getEventType().name());
        map.put("recordedAt", String.valueOf(event.getRecordedAt()));
        return map;
    }

    private static String label(RosterStatus status) {
        return switch (status) {
            case NONE -> "kayıt yok";
            case BOARDED -> "bindi";
            case DROPPED_OFF -> "indi";
            case ABSENT -> "gelmedi";
        };
    }

    private AttendanceCorrectionResponse toResponse(AttendanceCorrectionRequest request) {
        Trip trip = request.getTrip();
        RosterStatus current = statusResolver.currentStatus(trip.getId(), request.getStudent().getId());
        User requester = request.getRequestedBy();
        User decider = request.getDecidedBy();
        return new AttendanceCorrectionResponse(
                request.getId(),
                trip.getId(),
                trip.getBusinessDate(),
                trip.getDirection(),
                request.getStudent().getId(),
                request.getStudent().getFullName(),
                request.getKind(),
                request.getSourceStatus(),
                request.getRequestedStatus(),
                current,
                request.getStatus() == CorrectionStatus.PENDING && current != request.getSourceStatus(),
                request.getReason(),
                request.getStatus(),
                requester.getFullName(),
                requester.getRole(),
                request.getCreatedAt(),
                decider == null ? null : decider.getFullName(),
                request.getDecidedAt(),
                request.getDecisionNote(),
                request.getAppliedStatus());
    }
}
