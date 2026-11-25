package com.okulservisi.backend.vehicle.dto.request;

import jakarta.validation.constraints.Positive;

/** Kısmi güncelleme (PATCH, ADMIN). {@code isActive} de bu endpoint üzerinden (ADMIN) değiştirilir —
 * docs/API.md bu modül için ayrı bir deactivate alt-endpoint'i tanımlamamıştır. */
public record VehicleUpdateRequest(
        String plate,
        @Positive(message = "Kapasite pozitif olmalıdır.") Integer capacity,
        String model,
        Boolean isActive) {
}
