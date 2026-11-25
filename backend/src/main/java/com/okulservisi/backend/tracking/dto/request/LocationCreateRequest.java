package com.okulservisi.backend.tracking.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * docs/API.md "16. Konum takibi" — {@code POST /api/tracking/trips/{tripId}/locations}
 * {@code { lat, lng, recordedAt }}. {@code recordedAt} istemci (araç/mobil GPS) tarafından üretilir
 * ve OLDUĞU GİBİ kabul edilir (sunucu saatiyle değiştirilmez) — GPS donanımının kendi zaman damgası
 * doğruluk/gecikme bilgisini taşır, {@code GET /latest}'teki {@code staleSeconds} bu alana göre
 * hesaplanır.
 *
 * <p><b>Tasarım kararı — lat/lng neden {@code Double} (BigDecimal DEĞİL):</b> docs/API.md "Para JSON'da
 * string olarak taşınır" kuralı SADECE para alanları için geçerlidir; {@link
 * com.okulservisi.backend.common.config.JacksonConfig} bu yüzden TÜM {@code BigDecimal} alanlarını
 * global olarak 2 ondalık basamaklı string'e sabitler — GPS koordinatları (precision=9, scale=6) bu
 * sabit kesmeye uğrardı. Entity'de hâlâ {@code BigDecimal} (DB hassasiyeti için), ama DTO sınırında
 * sıradan JSON sayısına (double) çevrilir (bkz. {@code TrackingService}).
 */
public record LocationCreateRequest(
        @NotNull(message = "lat zorunludur.")
        @DecimalMin(value = "-90.0", message = "lat -90 ile 90 arasında olmalıdır.")
        @DecimalMax(value = "90.0", message = "lat -90 ile 90 arasında olmalıdır.")
        Double lat,

        @NotNull(message = "lng zorunludur.")
        @DecimalMin(value = "-180.0", message = "lng -180 ile 180 arasında olmalıdır.")
        @DecimalMax(value = "180.0", message = "lng -180 ile 180 arasında olmalıdır.")
        Double lng,

        @NotNull(message = "recordedAt zorunludur.") Instant recordedAt) {
}
