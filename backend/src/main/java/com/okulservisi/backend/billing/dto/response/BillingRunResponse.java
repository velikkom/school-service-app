package com.okulservisi.backend.billing.dto.response;

/** docs/API.md: "oluşturulan/atlanan sayılarını döndür ({created, skipped, period})". */
public record BillingRunResponse(String period, int created, int skipped) {
}
