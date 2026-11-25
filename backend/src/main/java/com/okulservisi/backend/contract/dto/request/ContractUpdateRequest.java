package com.okulservisi.backend.contract.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kısmi güncelleme (PATCH, ADMIN). {@code studentId}/{@code startDate}/{@code status} bu DTO ile
 * DEĞİŞTİRİLEMEZ — sözleşme tarafı veya başlangıcı değişmesi gereken durumda yeni sözleşme
 * oluşturulmalıdır (bkz. {@code AssignmentUpdateRequest} emsali); durum değişikliği ayrı bir
 * {@code POST /api/contracts/{id}/cancel} endpoint'i ile yapılır (gerekçe: ContractService javadoc'u).
 * Sadece ACTIVE durumdaki bir sözleşme güncellenebilir.
 */
public record ContractUpdateRequest(
        @Positive(message = "monthlyFee pozitif olmalıdır.") BigDecimal monthlyFee,
        @PositiveOrZero(message = "discountAmount negatif olamaz.") BigDecimal discountAmount,
        LocalDate endDate) {
}
