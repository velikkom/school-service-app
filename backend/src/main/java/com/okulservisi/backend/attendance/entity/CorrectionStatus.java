package com.okulservisi.backend.attendance.entity;

/** docs/API.md §24 — yoklama düzeltme talebi durumu. Geçişler: PENDING -> APPROVED | REJECTED (tek sefer). */
public enum CorrectionStatus {
    PENDING,
    APPROVED,
    REJECTED
}
