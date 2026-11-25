import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Yoklama düzeltme / inceleme talepleri (API §24). */
export const attendanceCorrectionsService = {
  list: (params) => http.get(`/attendance-corrections${qs(params)}`),
  get: (id) => http.get(`/attendance-corrections/${id}`),
  create: (body) => http.post("/attendance-corrections", body),
  approve: (id, body) => http.post(`/attendance-corrections/${id}/approve`, body ?? {}),
  reject: (id, reason) => http.post(`/attendance-corrections/${id}/reject`, { reason }),
};
