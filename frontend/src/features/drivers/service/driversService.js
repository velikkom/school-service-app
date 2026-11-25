import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Şoförler (API §6). */
export const driversService = {
  list: (params) => http.get(`/drivers${qs(params)}`),
  get: (id) => http.get(`/drivers/${id}`),
  me: () => http.get("/drivers/me"),
  create: (body) => http.post("/drivers", body),
  update: (id, body) => http.patch(`/drivers/${id}`, body),
};
