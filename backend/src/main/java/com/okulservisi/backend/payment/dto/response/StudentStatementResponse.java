package com.okulservisi.backend.payment.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record StudentStatementResponse(
        Long studentId,
        List<StatementEntryResponse> entries,
        BigDecimal currentBalance) {
}
