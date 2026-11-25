import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Öğrenciler (API §3), ekstre (§13), belge (§27). */
export const studentsService = {
  list: (params) => http.get(`/students${qs(params)}`),
  get: (id) => http.get(`/students/${id}`),
  create: (body) => http.post("/students", body),
  update: (id, body) => http.patch(`/students/${id}`, body),
  deactivate: (id) => http.post(`/students/${id}/deactivate`),
  /** Sprint 3: STUDENT hesabı bağı (API §3 "Öğrenci hesabı bağı"). */
  linkAccount: (id, userId) => http.put(`/students/${id}/account`, { userId }),
  unlinkAccount: (id) => http.del(`/students/${id}/account`),
  statement: (id) => http.get(`/students/${id}/statement`),
  statementDocumentPath: (id) => `/students/${id}/statement/document`,
};
