import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Araçlar (API §5). mine: şoförün en son seferinin aracı (404 = yok). */
export const vehiclesService = {
  list: (params) => http.get(`/vehicles${qs(params)}`),
  get: (id) => http.get(`/vehicles/${id}`),
  mine: () => http.get("/vehicles/mine"),
  create: (body) => http.post("/vehicles", body),
  update: (id, body) => http.patch(`/vehicles/${id}`, body),
};
