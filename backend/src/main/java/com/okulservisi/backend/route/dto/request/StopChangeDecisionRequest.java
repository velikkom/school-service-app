package com.okulservisi.backend.route.dto.request;

import jakarta.validation.constraints.Size;

/** docs/API.md §25 — onay notu (opsiyonel) / ret gerekçesi (ret için serviste zorunlu). */
public record StopChangeDecisionRequest(
        @Size(max = 500, message = "Gerekçe en fazla 500 karakter olabilir.") String note,
        @Size(max = 500, message = "Gerekçe en fazla 500 karakter olabilir.") String reason) {
}
