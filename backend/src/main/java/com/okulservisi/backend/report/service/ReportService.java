package com.okulservisi.backend.report.service;

import com.okulservisi.backend.report.dto.response.CollectionsReportResponse;
import com.okulservisi.backend.report.dto.response.DailySummaryResponse;
import com.okulservisi.backend.report.dto.response.ReceivablesReportResponse;
import java.time.LocalDate;

/** docs/API.md "14. Dashboard / Rapor" (bkz. docs/RBAC_MATRIX.md: {@code /api/reports/**} SADECE ADMIN). */
public interface ReportService {

    DailySummaryResponse dailySummary(LocalDate businessDate);

    CollectionsReportResponse collections(LocalDate from, LocalDate to);

    ReceivablesReportResponse receivables();
}
