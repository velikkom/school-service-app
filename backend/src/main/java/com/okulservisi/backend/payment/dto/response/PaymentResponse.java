package com.okulservisi.backend.payment.dto.response;

import com.okulservisi.backend.payment.entity.PaymentMethod;
import com.okulservisi.backend.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long studentId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String note,
        Instant cancelledAt,
        Instant createdAt) {
}
