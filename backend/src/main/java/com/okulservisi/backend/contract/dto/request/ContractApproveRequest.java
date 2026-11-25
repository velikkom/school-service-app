package com.okulservisi.backend.contract.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * docs/API.md §19: {@code POST /api/contracts/{id}/approve} (ADMIN) — SADECE {@code
 * status=PENDING_APPROVAL} iken izinli. {@code monthlyFee} zorunlu (ADMIN kurum varsayılanını görüp
 * değiştirebilir); {@code startDate}/{@code discountAmount} verilmezse mevcut değer korunur.
 */
public record ContractApproveRequest(
        @NotNull(message = "monthlyFee zorunludur.") @Positive(message = "monthlyFee pozitif olmalıdır.") BigDecimal monthlyFee,
        LocalDate startDate,
        @PositiveOrZero(message = "discountAmount negatif olamaz.") BigDecimal discountAmount) {
}
