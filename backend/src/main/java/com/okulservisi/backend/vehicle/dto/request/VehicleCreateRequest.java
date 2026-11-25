package com.okulservisi.backend.vehicle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** docs/API.md "5. Araç": plaka normalize+unique, {@code capacity} zorunlu pozitif tam sayı. */
public record VehicleCreateRequest(
        @NotBlank(message = "Plaka zorunludur.") String plate,
        @NotNull(message = "Kapasite zorunludur.") @Positive(message = "Kapasite pozitif olmalıdır.") Integer capacity,
        String model) {
}
