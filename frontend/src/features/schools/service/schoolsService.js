import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Okullar (API §20). */
export const schoolsService = {
  list: (params) => http.get(`/schools${qs(params)}`),
  get: (id) => http.get(`/schools/${id}`),
  create: (body) => http.post("/schools", body),
  update: (id, body) => http.patch(`/schools/${id}`, body),
};
