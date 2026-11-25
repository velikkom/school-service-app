package com.okulservisi.backend.attendance.service.impl;

import com.okulservisi.backend.attendance.dto.request.AbsenceNoticeCreateRequest;
import com.okulservisi.backend.attendance.dto.response.AbsenceNoticeResponse;
import com.okulservisi.backend.attendance.dto.response.RosterStatus;
import com.okulservisi.backend.attendance.entity.AbsenceNoticeStatus;
import com.okulservisi.backend.attendance.entity.TripAbsenceNotice;
import com.okulservisi.backend.attendance.repository.TripAbsenceNoticeRepository;
import com.okulservisi.backend.attendance.service.AttendanceStatusResolver;
import com.okulservisi.backend.attendance.service.TripAbsenceNoticeService;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.trip.policy.TripAccessGuard;
import com.okulservisi.backend.trip.repository.TripRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md §22. Kurallar (hepsi backend'de doğrulanır):
 * <ul>
 *   <li>Sadece AKTİF veli-öğrenci bağı olan veli (aksi 404 — kapsam dışı).</li>
 *   <li>Öğrenci o seferin yolcu listesinde (güzergâh+yön+iş günü aktif ataması) olmalı (aksi 404).</li>
 *   <li>Sefer PLANNED/IN_PROGRESS olmalı ve öğrenci için o seferde resmi yoklama kaydı olmamalı (409).</li>
 *   <li>Tekrar gönderim: aynı öğrenci+sefer için tek AKTİF bildirim — öğrenci satırı {@code FOR UPDATE}
 *       ile kilitlenir, eşzamanlı istekler sıraya girer; ikinci istek mevcut bildirimi döner. DB'de de
 *       {@code uq_trip_absence_notice_active} kısmi benzersiz indeksi vardır.</li>
 *   <li>Resmi yoklamaya ({@code attendance_event}) HİÇBİR yazma yapılmaz.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TripAbsenceNoticeServiceImpl implements TripAbsenceNoticeService {

    private final TripAbsenceNoticeRepository noticeRepository;
    private final TripRepository tripRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final StudentRouteAssignmentRepository assignmentRepository;
    private final AttendanceStatusResolver attendanceStatusResolver;
    private final TripAccessGuard tripAccessGuard;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Result submit(Long tripId, AbsenceNoticeCreateRequest request, AuthenticatedUser principal) {
        Parent parent = requireParent(principal);
        Long studentId = request.studentId();
        assertLinked(parent, studentId);
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> NotFoundException.of("Sefer", tripId));
        assertOnRoster(trip, studentId);

        // Aynı öğrenci için eşzamanlı gönderimleri serileştirir.
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> NotFoundException.of("Öğrenci", studentId));

        Optional<TripAbsenceNotice> existing =
                noticeRepository.findByTripIdAndStudentIdAndStatus(tripId, studentId, AbsenceNoticeStatus.ACTIVE);
        if (existing.isPresent()) {
            return new Result(toResponse(existing.get()), false);
        }

        assertTripOpen(trip);
        if (attendanceStatusResolver.currentStatus(tripId, studentId) != RosterStatus.NONE) {
            throw new ConflictException(
                    "Bu öğrenci için bu seferde yoklama kaydı girilmiş; servise binmeyecek bildirimi bırakılamaz.");
        }

        TripAbsenceNotice notice = new TripAbsenceNotice();
        notice.setTrip(trip);
        notice.setStudent(student);
        notice.setParent(parent);
        notice.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        notice.setStatus(AbsenceNoticeStatus.ACTIVE);
        TripAbsenceNotice saved = noticeRepository.saveAndFlush(notice);
        entityManager.refresh(saved); // created_at DB tarafında üretilir
        return new Result(toResponse(saved), true);
    }

    @Override
    @Transactional
    public AbsenceNoticeResponse withdraw(Long tripId, Long noticeId, AuthenticatedUser principal) {
        Parent parent = requireParent(principal);
        TripAbsenceNotice notice = noticeRepository.findByIdAndTripIdForUpdate(noticeId, tripId)
                .orElseThrow(() -> NotFoundException.of("Bildirim", noticeId));
        Long studentId = notice.getStudent().getId();
        if (!parentStudentLinkRepository.existsByParentIdAndStudentIdAndActiveTrue(parent.getId(), studentId)) {
            throw NotFoundException.of("Bildirim", noticeId);
        }
        if (notice.getStatus() != AbsenceNoticeStatus.ACTIVE) {
            throw new ConflictException("Bu bildirim zaten geri alınmış.");
        }
        Trip trip = notice.getTrip();
        assertTripOpen(trip);
        if (attendanceStatusResolver.currentStatus(tripId, studentId) != RosterStatus.NONE) {
            throw new ConflictException("Yoklama kaydı girildiği için bildirim artık geri alınamaz.");
        }
        notice.setStatus(AbsenceNoticeStatus.WITHDRAWN);
        notice.setWithdrawnAt(Instant.now());
        return toResponse(noticeRepository.saveAndFlush(notice));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AbsenceNoticeResponse> listActive(Long tripId, AuthenticatedUser principal) {
        tripAccessGuard.assertStaffAssigned(tripId, principal);
        return noticeRepository.findByTripIdAndStatusOrderByCreatedAtAsc(tripId, AbsenceNoticeStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    private Parent requireParent(AuthenticatedUser principal) {
        return parentRepository.findByUserId(principal.getUserId())
                .orElseThrow(() -> new NotFoundException("Veli profili bulunamadı."));
    }

    private void assertLinked(Parent parent, Long studentId) {
        if (!parentStudentLinkRepository.existsByParentIdAndStudentIdAndActiveTrue(parent.getId(), studentId)) {
            throw NotFoundException.of("Öğrenci", studentId);
        }
    }

    private void assertOnRoster(Trip trip, Long studentId) {
        if (!assignmentRepository.isOnRoster(studentId, trip.getRoute().getId(), trip.getDirection(),
                trip.getBusinessDate())) {
            // Varlık bilgisini sızdırmamak için 404 (öğrenci bu seferin yolcusu değil).
            throw NotFoundException.of("Sefer", trip.getId());
        }
    }

    private void assertTripOpen(Trip trip) {
        if (trip.getStatus() != TripStatus.PLANNED && trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new ConflictException("Sefer kapandığı için (durum: " + trip.getStatus() + ") bu işlem yapılamaz.");
        }
    }

    private AbsenceNoticeResponse toResponse(TripAbsenceNotice notice) {
        return new AbsenceNoticeResponse(
                notice.getId(),
                notice.getTrip().getId(),
                notice.getStudent().getId(),
                notice.getStudent().getFullName(),
                notice.getParent().getFullName(),
                notice.getNote(),
                notice.getStatus(),
                notice.getCreatedAt(),
                notice.getWithdrawnAt());
    }
}
