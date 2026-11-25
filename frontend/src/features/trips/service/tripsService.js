import { http } from "@/lib/http/client";
import { toQueryString as qs } from "@/lib/http/queryString";

/** Seferler, yolcu listesi, yoklama, veli bildirimleri ve operasyon kayıtları (API §9, §10, §21–§23). */
export const tripsService = {
  list: (params) => http.get(`/trips${qs(params)}`),
  mine: (businessDate) => http.get(`/trips/mine${qs({ businessDate })}`),
  get: (id) => http.get(`/trips/${id}`),
  create: (body) => http.post("/trips", body),
  update: (id, body) => http.patch(`/trips/${id}`, body),
  start: (id) => http.post(`/trips/${id}/start`),
  complete: (id) => http.post(`/trips/${id}/complete`),
  cancel: (id, reason) => http.post(`/trips/${id}/cancel`, { reason }),
  roster: (id) => http.get(`/trips/${id}/roster`),
  recordBoarding: (tripId, studentId) => http.post(`/trips/${tripId}/attendance-events/boardings`, { studentId }),
  recordDropoff: (tripId, studentId) => http.post(`/trips/${tripId}/attendance-events/dropoffs`, { studentId }),
  recordAbsence: (tripId, studentId, reason) => http.post(`/trips/${tripId}/attendance-events/absences`, { studentId, reason: reason || undefined }),
  absenceNotices: (tripId) => http.get(`/trips/${tripId}/absence-notices`),
  incidents: (tripId) => http.get(`/trips/${tripId}/incidents`),
  createIncident: (tripId, body) => http.post(`/trips/${tripId}/incidents`, body),
  resolveIncident: (tripId, incidentId, body) => http.post(`/trips/${tripId}/incidents/${incidentId}/resolve`, body),
};
