import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Raporlar (API §14) — sadece ADMIN. */
export const reportsService = {
  dailySummary: (date) => http.get(`/reports/operations/daily-summary${qs({ date })}`),
  collections: (from, to) => http.get(`/reports/finance/collections${qs({ from, to })}`),
  receivables: () => http.get("/reports/finance/receivables"),
};
