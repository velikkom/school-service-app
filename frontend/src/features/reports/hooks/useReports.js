"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { reportsService } from "@/features/reports/service/reportsService";

export const reportKeys = {
  dailySummary: (date) => ["reports", "daily-summary", date],
  collections: (from, to) => ["reports", "collections", from, to],
  receivables: () => ["reports", "receivables"],
};

export function useDailySummaryReport(date) {
  return useQuery({
    queryKey: reportKeys.dailySummary(date),
    queryFn: () => reportsService.dailySummary(date),
    placeholderData: keepPreviousData,
  });
}

export function useCollectionsReport(from, to, { enabled = true } = {}) {
  return useQuery({
    queryKey: reportKeys.collections(from, to),
    queryFn: () => reportsService.collections(from, to),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export function useReceivablesReport() {
  return useQuery({ queryKey: reportKeys.receivables(), queryFn: reportsService.receivables });
}

/** Yönteme göre kırılımda her yöntem için tutar ve toplam içindeki oran (çubuk genişliği). */
export function methodBreakdown(report, methods) {
  const byMethod = Object.fromEntries((report?.byMethod ?? []).map((m) => [m.method, m.amount]));
  const total = Number(report?.totalCollected ?? 0);
  return methods.map((method) => {
    const amount = byMethod[method] ?? "0.00";
    const pct = total > 0 ? Math.round((Number(amount) / total) * 100) : 0;
    return { method, amount, pct: Math.max(0, Math.min(100, pct)) };
  });
}
