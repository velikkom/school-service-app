package com.okulservisi.backend.parent.dto.request;

import jakarta.validation.constraints.Pattern;

/** Kısmi güncelleme (PATCH, ADMIN). {@code userId} buradan değiştirilemez. */
public record ParentUpdateRequest(
        String fullName,
        String phone,
        @Pattern(regexp = "\\d{11}", message = "T.C. kimlik numarası 11 hane olmalıdır.") String tcNo) {
}
