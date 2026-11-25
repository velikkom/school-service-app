import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Kullanıcı yönetimi (API §2). */
export const usersService = {
  list: (params) => http.get(`/users${qs(params)}`),
  get: (id) => http.get(`/users/${id}`),
  create: (body) => http.post("/users", body),
  update: (id, body) => http.patch(`/users/${id}`, body),
  changeRole: (id, body) => http.post(`/users/${id}/role`, body),
  deactivate: (id) => http.post(`/users/${id}/deactivate`),
  activate: (id) => http.post(`/users/${id}/activate`),
};
