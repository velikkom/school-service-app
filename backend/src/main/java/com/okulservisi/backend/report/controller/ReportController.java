package com.okulservisi.backend.report.controller;

import com.okulservisi.backend.report.api.ReportApiPaths;
import com.okulservisi.backend.report.dto.response.CollectionsReportResponse;
import com.okulservisi.backend.report.dto.response.DailySummaryResponse;
import com.okulservisi.backend.report.dto.response.ReceivablesReportResponse;
import com.okulservisi.backend.report.service.ReportService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "14. Dashboard / Rapor" (bkz. docs/RBAC_MATRIX.md: SADECE ADMIN). */
@RestController
@RequestMapping(ReportApiPaths.BASE)
@AdminOnly
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * NOT: {@code date}/{@code from}/{@code to} BİLEREK {@code required = false} — eksik parametre
     * durumunda Spring'in fırlattığı {@code MissingServletRequestParameterException}
     * {@code GlobalExceptionHandler}'da YAKALANMIYOR (yanlışlıkla 500'e düşerdi); bunun yerine
     * parametreler opsiyonel kabul edilip {@code ReportService} kendi anlamlı 400 mesajını üretir
     * (bkz. {@code BadRequestException} — tutarlı hata gövdesi için).
     */
    @GetMapping(ReportApiPaths.DAILY_SUMMARY)
    public DailySummaryResponse dailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.dailySummary(date);
    }

    @GetMapping(ReportApiPaths.COLLECTIONS)
    public CollectionsReportResponse collections(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.collections(from, to);
    }

    @GetMapping(ReportApiPaths.RECEIVABLES)
    public ReceivablesReportResponse receivables() {
        return reportService.receivables();
    }
}
