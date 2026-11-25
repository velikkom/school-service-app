import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Hostesler (API §6). */
export const hostessesService = {
  list: (params) => http.get(`/hostesses${qs(params)}`),
  get: (id) => http.get(`/hostesses/${id}`),
  me: () => http.get("/hostesses/me"),
  create: (body) => http.post("/hostesses", body),
  update: (id, body) => http.patch(`/hostesses/${id}`, body),
};
