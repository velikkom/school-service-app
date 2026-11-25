package com.okulservisi.backend.trip.entity;

/**
 * docs/API.md §23. Bilgi kayıtları (DELAY/BREAKDOWN/ROAD_CLOSURE) {@code RECORDED}; iptal talebi
 * {@code OPEN} → {@code APPROVED} | {@code REJECTED} (tek sefer, sadece ADMIN).
 */
public enum TripIncidentStatus {
    RECORDED,
    OPEN,
    APPROVED,
    REJECTED
}
