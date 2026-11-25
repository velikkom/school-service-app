package com.okulservisi.backend.contract.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * docs/API.md "11. Sözleşme": {@code { studentId, monthlyFee, startDate, endDate?, discountAmount? }}.
 * {@code monthlyFee>0}, {@code discountAmount>=0} — DB CHECK zaten var, ama ham constraint hatası
 * yerine kullanıcıya temiz bir {@code 400 VALIDATION_ERROR}/{@code fieldErrors} göstermek için burada
 * da doğrulanır (bkz. CLAUDE.md görevi). {@code discountAmount} verilmezse (null) servis katmanında
 * {@link BigDecimal#ZERO} uygulanır.
 */
public record ContractCreateRequest(
        @NotNull(message = "studentId zorunludur.") Long studentId,
        @NotNull(message = "monthlyFee zorunludur.") @Positive(message = "monthlyFee pozitif olmalıdır.") BigDecimal monthlyFee,
        @NotNull(message = "startDate zorunludur.") LocalDate startDate,
        LocalDate endDate,
        @PositiveOrZero(message = "discountAmount negatif olamaz.") BigDecimal discountAmount) {
}
