import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Durak değişikliği talepleri (API §25). options: studentId ve direction zorunlu. */
export const stopChangeRequestsService = {
  options: (studentId, direction) => http.get(`/stop-change-requests/options${qs({ studentId, direction })}`),
  list: (params) => http.get(`/stop-change-requests${qs(params)}`),
  create: (body) => http.post("/stop-change-requests", body),
  approve: (id, note) => http.post(`/stop-change-requests/${id}/approve`, { note: note || undefined }),
  reject: (id, reason) => http.post(`/stop-change-requests/${id}/reject`, { reason }),
};
