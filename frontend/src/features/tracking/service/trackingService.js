import { http } from "@/lib/http/client";

/** Konum takibi (API §16). latest: 404 = konum yok / sefer aktif değil. */
export const trackingService = {
  latest: (tripId) => http.get(`/tracking/trips/${tripId}/latest`),
  send: (tripId, body) => http.post(`/tracking/trips/${tripId}/locations`, body),
};
