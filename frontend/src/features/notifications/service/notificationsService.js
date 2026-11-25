import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Bildirimler (API §15). Liste IN_APP + EMAIL satırı döndürebilir; ekranda channel=IN_APP gösterilir. */
export const notificationsService = {
  list: (params) => http.get(`/notifications${qs(params)}`),
  markRead: (id) => http.post(`/notifications/${id}/read`),
  broadcast: (body) => http.post("/notifications/broadcast", body),
};
