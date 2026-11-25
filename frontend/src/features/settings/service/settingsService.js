import { http } from "@/lib/http/client";

/** Kurum ayarları (API §17). */
export const settingsService = {
  get: () => http.get("/settings"),
  update: (body) => http.patch("/settings", body),
};
