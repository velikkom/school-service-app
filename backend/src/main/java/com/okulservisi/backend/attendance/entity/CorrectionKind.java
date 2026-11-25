package com.okulservisi.backend.attendance.entity;

/**
 * docs/API.md §24 — {@code CORRECTION}: DRIVER/HOSTESS'in kendi seferindeki kayıt için düzeltme talebi
 * (istenen durum zorunlu); {@code REVIEW}: PARENT'ın kendi çocuğunun kaydı için inceleme talebi.
 */
public enum CorrectionKind {
    CORRECTION,
    REVIEW
}
