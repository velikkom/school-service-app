package com.okulservisi.backend.attendance.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.trip.entity.Trip;
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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code trip_absence_notice} (V14) — velinin "bu sefer servise binmeyecek" bildirimi. Resmi yoklama
 * kaydı ({@link AttendanceEvent}) DEĞİLDİR ve onu değiştirmez; görevli yine biniş kaydedebilir.
 * Aynı sefer+öğrenci için tek AKTİF bildirim ({@code uq_trip_absence_notice_active}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "trip_absence_notice")
public class TripAbsenceNotice extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @Column(name = "note")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AbsenceNoticeStatus status = AbsenceNoticeStatus.ACTIVE;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
