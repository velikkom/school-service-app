package com.okulservisi.backend.driver.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** docs/API.md "6. Şoför/Hostes": her Driver bir User (role=DRIVER) ile 1-1 (docs/DATABASE_DESIGN.md). */
public record DriverCreateRequest(
        @NotNull(message = "userId zorunludur.") Long userId,
        @NotBlank(message = "Lisans numarası zorunludur.") String licenseNo) {
}
