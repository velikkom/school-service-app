package com.okulservisi.backend.trip.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
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
 * {@code trip_incident} (V14, docs/API.md §23) — gecikme/arıza/yol kapanması kaydı veya iptal talebi.
 * İptal talebi açılması seferin durumunu DEĞİŞTİRMEZ; sadece ADMIN talebi onaylayınca sefer iptal olur.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "trip_incident")
public class TripIncident extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TripIncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TripIncidentStatus status;

    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @Column(name = "note", nullable = false)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_user_id", nullable = false)
    private User reportedBy;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
