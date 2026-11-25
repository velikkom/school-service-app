package com.okulservisi.backend.report.dto.response;

import com.okulservisi.backend.payment.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * docs/API.md "14. Dashboard / Rapor" — {@code GET /api/reports/finance/collections?from=&to=}.
 * {@code from}/{@code to} DAHİL (inclusive) tarih aralığıdır (bkz. {@code ReportService} javadoc'u —
 * Europe/Istanbul gün sınırları Instant'a çevrilir). SADECE {@code status=COMPLETED} ödemeler
 * sayılır.
 */
public record CollectionsReportResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalCollected,
        List<MethodAmount> byMethod) {

    public record MethodAmount(PaymentMethod method, BigDecimal amount) {
    }
}
