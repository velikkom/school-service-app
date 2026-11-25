package com.okulservisi.backend.trip.dto.request;

import com.okulservisi.backend.trip.entity.TripIncidentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/API.md §23 {@code POST /api/trips/{id}/incidents}. {@code delayMinutes} sadece DELAY için zorunlu. */
public record TripIncidentCreateRequest(
        @NotNull(message = "type zorunludur.") TripIncidentType type,
        @Min(value = 1, message = "Gecikme en az 1 dakika olmalıdır.")
        @Max(value = 600, message = "Gecikme en fazla 600 dakika olabilir.") Integer delayMinutes,
        @NotBlank(message = "Açıklama zorunludur.")
        @Size(max = 500, message = "Açıklama en fazla 500 karakter olabilir.") String note) {
}
