package com.okulservisi.backend.tracking.dto.response;

import java.time.Instant;

/** {@code POST /api/tracking/trips/{tripId}/locations} yanıtı — kaydedilen konumun onayı. */
public record LocationAckResponse(Long id, Double lat, Double lng, Instant recordedAt) {
}
