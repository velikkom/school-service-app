package com.okulservisi.backend.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Gerekçeli ret ({@code { reason }}, zorunlu) — docs/API.md §24. */
public record DecisionReasonRequest(
        @NotBlank(message = "Gerekçe zorunludur.")
        @Size(max = 500, message = "Gerekçe en fazla 500 karakter olabilir.") String reason) {
}
