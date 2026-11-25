package com.okulservisi.backend.route.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalTime;

/** docs/API.md "7. Güzergâh/Durak": {@code Stop}: {@code name}, {@code sequenceNo} (route içinde
 * benzersiz), {@code plannedTime}. {@code routeId} path'ten gelir, body'de tekrar edilmez. */
public record StopCreateRequest(
        @NotBlank(message = "Ad zorunludur.") String name,
        @NotNull(message = "sequenceNo zorunludur.") @Positive(message = "sequenceNo pozitif olmalıdır.") Integer sequenceNo,
        @NotNull(message = "plannedTime zorunludur.") LocalTime plannedTime) {
}
