import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Sözleşmeler ve öğrenci başvuruları (API §11, §19). */
export const contractsService = {
  list: (params) => http.get(`/contracts${qs(params)}`),
  get: (id) => http.get(`/contracts/${id}`),
  create: (body) => http.post("/contracts", body),
  update: (id, body) => http.patch(`/contracts/${id}`, body),
  cancel: (id) => http.post(`/contracts/${id}/cancel`),
  approve: (id, body) => http.post(`/contracts/${id}/approve`, body),
  reject: (id, reason) => http.post(`/contracts/${id}/reject`, { reason: reason || undefined }),
};
