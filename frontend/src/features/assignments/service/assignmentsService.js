import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Öğrenci–güzergâh/durak atamaları (API §8). */
export const assignmentsService = {
  list: (params) => http.get(`/assignments${qs(params)}`),
  get: (id) => http.get(`/assignments/${id}`),
  create: (body) => http.post("/assignments", body),
  update: (id, body) => http.patch(`/assignments/${id}`, body),
};
