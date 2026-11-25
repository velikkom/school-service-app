package com.okulservisi.backend.trip.dto.request;

import jakarta.validation.constraints.Size;

/** docs/API.md §21 {@code POST /api/trips/{id}/cancel} (SADECE ADMIN) — {@code { reason? }}. */
public record TripCancelRequest(@Size(max = 500, message = "Gerekçe en fazla 500 karakter olabilir.") String reason) {
}
