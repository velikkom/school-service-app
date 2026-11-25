package com.okulservisi.backend.attendance.entity;

import com.okulservisi.backend.attendance.dto.response.RosterStatus;
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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code attendance_correction_request} (V14, docs/API.md §24). {@code sourceStatus}: talep açıldığı
 * andaki yoklama durumu (anlık görüntü) — onay anında güncel durum bundan farklıysa talep verisiyle
 * üzerine YAZILMAZ (409). Aynı sefer+öğrenci için tek bekleyen talep ({@code uq_acr_pending}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "attendance_correction_request")
public class AttendanceCorrectionRequest extends BaseAuditable {

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
    @Column(name = "kind", nullable = false)
    private CorrectionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_status", nullable = false)
    private RosterStatus sourceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_status")
    private RosterStatus requestedStatus;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CorrectionStatus status = CorrectionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_status")
    private RosterStatus appliedStatus;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
