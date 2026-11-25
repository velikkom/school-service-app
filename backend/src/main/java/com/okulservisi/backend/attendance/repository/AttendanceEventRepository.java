package com.okulservisi.backend.attendance.repository;
import com.okulservisi.backend.attendance.entity.AttendanceEvent;
import com.okulservisi.backend.attendance.entity.AttendanceEventType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * NOT (Faz F): {@code JpaSpecificationExecutor} Backend Agent tarafından EKLENDİ (geri kalanı
 * değiştirilmedi) — {@code GET /api/students/{id}/attendance} için opsiyonel {@code from}/{@code to}
 * ve rol bazlı kapsam filtrelerinin (bkz. {@code AttendanceEventSpecifications}) birleştirilmesi
 * başka türlü (sabit {@code findByStudentIdAndRecordedAtBetween} tek başına) mümkün değildi.
 * NOT (Faz H): {@link #countByTripBusinessDateAndEventType} (docs/API.md "14. Dashboard / Rapor" günlük
 * özet için) EKLENDİ.
 * NOT (Faz I): {@link #findByTripIdAndCorrectionFalse} ({@code GET /api/trips/{id}/roster} için —
 * düzeltme OLMAYAN orijinal kayıtlar) EKLENDİ.
 */
public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long>,
        JpaSpecificationExecutor<AttendanceEvent> {

    Optional<AttendanceEvent> findByTripIdAndStudentIdAndEventTypeAndCorrectionFalse(
            Long tripId, Long studentId, AttendanceEventType eventType);

    List<AttendanceEvent> findByStudentIdAndRecordedAtBetween(Long studentId, Instant from, Instant to);

    List<AttendanceEvent> findByTripIdAndCorrectionFalse(Long tripId);

    /** 2026-11-18: bir öğrencinin bir seferdeki geçerli (voided olmayan) orijinal kayıtları. */
    List<AttendanceEvent> findByTripIdAndStudentIdAndCorrectionFalse(Long tripId, Long studentId);

    /**
     * 2026-11-18: onaylanan düzeltmede bir kaydı geçersiz işaretler (silmez). Native sorgu —
     * {@code @SQLRestriction} sadece okuma sorgularını filtreler.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE attendance_event SET voided_at = now(), voided_by_correction_id = :correctionId "
            + "WHERE id = :id AND voided_at IS NULL", nativeQuery = true)
    int voidEvent(@Param("id") Long id, @Param("correctionId") Long correctionId);

    /**
     * {@code GET /api/reports/operations/daily-summary?date=} — o {@code businessDate}'teki (trip
     * üzerinden) toplam biniş/iniş/devamsızlık sayısı. SADECE düzeltme OLMAYAN ({@code is_correction
     * = false}) orijinal kayıtlar sayılır (bkz. {@code attendance_event} UNIQUE(trip_id, student_id,
     * event_type) kuralı — düzeltmeler ayrı satırdır, iki kez saymamak için hariç tutulur).
     */
    @Query("select count(a) from AttendanceEvent a "
            + "where a.trip.businessDate = :businessDate and a.eventType = :eventType and a.correction = false")
    long countByTripBusinessDateAndEventType(@Param("businessDate") LocalDate businessDate,
            @Param("eventType") AttendanceEventType eventType);
}
