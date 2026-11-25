package com.okulservisi.backend.trip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/API.md §23 {@code POST /api/trips/{id}/incidents/{incidentId}/resolve} (ADMIN) — gerekçe zorunlu. */
public record TripIncidentResolveRequest(
        @NotNull(message = "approve zorunludur.") Boolean approve,
        @NotBlank(message = "Gerekçe zorunludur.")
        @Size(max = 500, message = "Gerekçe en fazla 500 karakter olabilir.") String note) {
}
