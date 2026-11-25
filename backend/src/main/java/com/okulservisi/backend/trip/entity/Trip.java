package com.okulservisi.backend.trip.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.vehicle.entity.Vehicle;
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
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code trip} tablosu — sefer. Optimistic locking için {@code version} alanı (eşzamanlı durum
 * geçişi çakışmalarını önler, state machine kontrolü service katmanında yapılır).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "trip")
public class Trip extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hostess_id")
    private Hostess hostess;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private RouteDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TripStatus status = TripStatus.PLANNED;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** 2026-11-18 (V14): planlanan kalkış saati (Europe/Istanbul yerel saat); eski seferlerde NULL. */
    @Column(name = "planned_start_time")
    private LocalTime plannedStartTime;

    /** 2026-11-18 (V14): ADMIN iptal gerekçesi (doğrudan iptal veya onaylanan iptal talebi). */
    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
