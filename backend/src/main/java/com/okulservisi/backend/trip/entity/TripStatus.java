package com.okulservisi.backend.trip.entity;

/** docs/API.md enum sözlüğü — sefer durumu (state machine: PLANNED -> IN_PROGRESS -> COMPLETED, veya CANCELLED). */
public enum TripStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
