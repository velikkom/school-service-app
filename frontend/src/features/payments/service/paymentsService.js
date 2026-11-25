import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Ödemeler (API §13) ve makbuz belgesi (§27). create: Idempotency-Key başlığıyla. */
export const paymentsService = {
  list: (params) => http.get(`/payments${qs(params)}`),
  get: (id) => http.get(`/payments/${id}`),
  create: (body, idempotencyKey) => http.post("/payments", body, { headers: { "Idempotency-Key": idempotencyKey } }),
  cancel: (id) => http.post(`/payments/${id}/cancel`),
  receiptPath: (id) => `/payments/${id}/receipt`,
};
