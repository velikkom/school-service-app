package com.okulservisi.backend.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * docs/API.md "12. Aylık ücret / tahakkuk": {@code POST /api/billing/run { period: "2026-10" }}.
 * {@code period} kasıtlı olarak {@code String} ("YYYY-MM") taşınır — DB'deki {@code monthly_charge.period
 * char(7)} ile birebir aynı tel formatı, {@code YearMonth} query-param dönüşümünün (Spring'in
 * yerleşik formatter desteği sadece LocalDate/LocalDateTime/LocalTime/ZonedDateTime/OffsetDateTime
 * için var, YearMonth için YOK) getireceği gereksiz riskten kaçınır; servis katmanında
 * {@code YearMonth.parse(...)} ile doğrulanıp kullanılır.
 */
public record BillingRunRequest(
        @NotBlank(message = "period zorunludur.")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "period 'YYYY-MM' formatında olmalıdır (örn. '2026-10').")
        String period) {
}
