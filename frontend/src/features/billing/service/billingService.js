import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Tahakkuk (API §12). */
export const billingService = {
  run: (period) => http.post("/billing/run", { period }),
  charges: (params) => http.get(`/billing/charges${qs(params)}`),
};
