package com.okulservisi.backend.tracking.dto.response;

import java.time.Instant;

/**
 * docs/API.md "16. Konum takibi" — {@code GET /api/tracking/trips/{tripId}/latest} yanıtı.
 * {@code isOffline = staleSeconds > 90} (bkz. {@code TrackingService} sabiti) — alan adı JSON sözleşmesi
 * gereği BİREBİR {@code isOffline} (record bileşen adı Jackson tarafından olduğu gibi serileştirilir,
 * JavaBean {@code isX}/{@code getX} getter'larındaki "is" düşürme kuralı record'larda uygulanmaz).
 */
public record LocationResponse(Double lat, Double lng, Instant recordedAt, long staleSeconds, boolean isOffline) {
}
