import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Güzergâhlar ve duraklar (API §7). Duraklar düz dizi. */
export const routesService = {
  list: (params) => http.get(`/routes${qs(params)}`),
  get: (id) => http.get(`/routes/${id}`),
  create: (body) => http.post("/routes", body),
  update: (id, body) => http.patch(`/routes/${id}`, body),
  stops: (routeId) => http.get(`/routes/${routeId}/stops`),
  createStop: (routeId, body) => http.post(`/routes/${routeId}/stops`, body),
  updateStop: (routeId, stopId, body) => http.patch(`/routes/${routeId}/stops/${stopId}`, body),
  deleteStop: (routeId, stopId) => http.del(`/routes/${routeId}/stops/${stopId}`),
};
