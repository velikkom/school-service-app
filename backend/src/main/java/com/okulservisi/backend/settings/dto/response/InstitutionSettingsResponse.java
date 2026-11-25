package com.okulservisi.backend.settings.dto.response;

import java.math.BigDecimal;

/** docs/API.md "17. Kurum ayarları" — {@code GET /api/settings}. */
public record InstitutionSettingsResponse(
        Long id, String name, String address, String phone, String reportHeaderNote, BigDecimal defaultMonthlyFee) {
}
