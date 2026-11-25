package com.okulservisi.backend.report.api;

/** docs/API.md "14. Dashboard / Rapor" — {@code ReportController} mapping sabitleri. */
public final class ReportApiPaths {

    private ReportApiPaths() {
    }

    public static final String BASE = "/api/reports";
    public static final String DAILY_SUMMARY = "/operations/daily-summary";
    public static final String COLLECTIONS = "/finance/collections";
    public static final String RECEIVABLES = "/finance/receivables";
}
