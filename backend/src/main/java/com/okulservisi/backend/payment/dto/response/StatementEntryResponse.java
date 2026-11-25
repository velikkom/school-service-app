package com.okulservisi.backend.payment.dto.response;

import com.okulservisi.backend.payment.entity.PaymentMethod;
import com.okulservisi.backend.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * docs/API.md "13. Ödeme": {@code GET /api/students/{id}/statement} — borç/ödeme/bakiye kronolojik
 * ekstre tek bir satırı. {@code type="CHARGE"} ise {@code period} dolu, {@code method}/{@code
 * paymentStatus} null'dur; {@code type="PAYMENT"} ise tersi. İptal edilen ödemeler de listede
 * görünür ({@code paymentStatus=CANCELLED}) ama {@code runningBalance} hesaplamasına DAHİL EDİLMEZ
 * (bkz. {@code StudentStatementService} — docs/DATABASE_DESIGN.md "Bakiye hesaplama" zaten
 * {@code status='COMPLETED'} filtresiyle tanımlı).
 */
public record StatementEntryResponse(
        String type,
        Instant occurredAt,
        String period,
        PaymentMethod method,
        PaymentStatus paymentStatus,
        BigDecimal amount,
        BigDecimal runningBalance) {
}
