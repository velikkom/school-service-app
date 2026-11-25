import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Hesap kayıt talepleri (API §18). */
export const registrationRequestsService = {
  list: (params) => http.get(`/registration-requests${qs(params)}`),
  get: (id) => http.get(`/registration-requests/${id}`),
  approve: (id, body) => http.post(`/registration-requests/${id}/approve`, body),
  reject: (id, reason) => http.post(`/registration-requests/${id}/reject`, { reason: reason || undefined }),
};
