package com.okulservisi.backend.billing.dto.response;

import java.math.BigDecimal;

public record MonthlyChargeResponse(
        Long id,
        Long studentId,
        Long contractId,
        String period,
        BigDecimal amount) {
}
