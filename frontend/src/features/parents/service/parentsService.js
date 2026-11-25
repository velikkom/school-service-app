import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Veliler ve veli–öğrenci bağları (API §4). */
export const parentsService = {
  list: (params) => http.get(`/parents${qs(params)}`),
  get: (id) => http.get(`/parents/${id}`),
  me: () => http.get("/parents/me"),
  create: (body) => http.post("/parents", body),
  update: (id, body) => http.patch(`/parents/${id}`, body),
  links: (params) => http.get(`/parent-student-links${qs(params)}`),
  createLink: (body) => http.post("/parent-student-links", body),
  revokeLink: (id) => http.post(`/parent-student-links/${id}/revoke`),
};
