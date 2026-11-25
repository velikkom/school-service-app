package com.okulservisi.backend.payment.dto.request;

import com.okulservisi.backend.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * docs/API.md "13. Ödeme": {@code { studentId, amount, method, note? }}. {@code Idempotency-Key}
 * HTTP header'ında taşınır (gövdede DEĞİL — docs/API.md kuralı), bkz. {@code PaymentController.create}.
 */
public record PaymentCreateRequest(
        @NotNull(message = "studentId zorunludur.") Long studentId,
        @NotNull(message = "amount zorunludur.") @Positive(message = "amount pozitif olmalıdır.") BigDecimal amount,
        @NotNull(message = "method zorunludur.") PaymentMethod method,
        String note) {
}
