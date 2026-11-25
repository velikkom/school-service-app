package com.okulservisi.backend.student.dto.request;

import jakarta.validation.constraints.NotNull;

/** Sprint 3 — {@code PUT /api/students/{id}/account} gövdesi (bkz. docs/API.md §3 "Öğrenci hesabı bağı"). */
public record StudentAccountLinkRequest(
        @NotNull(message = "Bağlanacak kullanıcı hesabı seçilmelidir.") Long userId) {
}
