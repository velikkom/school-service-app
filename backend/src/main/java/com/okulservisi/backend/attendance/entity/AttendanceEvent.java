package com.okulservisi.backend.attendance.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code attendance_event} — biniş/iniş/devamsızlık kaydı. Düzeltmeler ({@code isCorrection=true})
 * orijinal kaydı silmeden {@code correctedEvent} ile referans alarak audit izli eklenir.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attendance_event")
@SQLRestriction("voided_at IS NULL")
public class AttendanceEvent extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AttendanceEventType eventType;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private User recordedBy;

    @Column(name = "reason")
    private String reason;

    @Column(name = "is_correction", nullable = false)
    private boolean correction = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_event_id")
    private AttendanceEvent correctedEvent;

    /**
     * 2026-11-18 (V14): onaylanan bir yoklama düzeltmesi bu kaydı SİLMEZ, geçersiz işaretler. Entity
     * seviyesindeki {@code @SQLRestriction} sayesinde geçersiz kayıtlar HİÇBİR JPA sorgusunda (roster,
     * yoklama geçmişi, rapor sayımları, tekrar-kayıt/çelişki kontrolleri) görünmez; denetim izi
     * {@code audit_log} ve {@code attendance_correction_request} tablolarında kalır.
     */
    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by_correction_id")
    private Long voidedByCorrectionId;

    /** 2026-11-18: bu kayıt bir düzeltme onayıyla oluşturulduysa ilgili talep id'si. */
    @Column(name = "created_by_correction_id")
    private Long createdByCorrectionId;
}
