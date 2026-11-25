package com.okulservisi.backend.route.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.student.entity.Student;
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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code stop_change_request} (V14, docs/API.md §25) — velinin bağlı çocuğu için durak değişikliği
 * talebi. ADMIN onayı yeni/güncellenmiş bir {@link StudentRouteAssignment} üretir
 * ({@code resultingAssignment}). Aynı öğrenci+yön için tek bekleyen talep ({@code uq_scr_pending}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stop_change_request")
public class StopChangeRequest extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private RouteDirection direction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_stop_id", nullable = false)
    private Stop requestedStop;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StopChangeRequestStatus status = StopChangeRequestStatus.PENDING;

    @Column(name = "decision_note")
    private String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_assignment_id")
    private StudentRouteAssignment resultingAssignment;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
