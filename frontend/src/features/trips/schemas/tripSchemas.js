import { z } from "zod";

const id = (msg) => z.string().min(1, msg);

export const tripCreateSchema = z.object({
  businessDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Tarih seçin."),
  plannedStartTime: z.string().optional(),
  routeId: id("Güzergâh seçin."),
  vehicleId: id("Araç seçin."),
  driverId: id("Şoför seçin."),
  hostessId: z.string().optional(),
});

export const tripEditSchema = z.object({
  plannedStartTime: z.string().optional(),
  vehicleId: id("Araç seçin."),
  driverId: id("Şoför seçin."),
  hostessId: z.string().optional(),
});

export const incidentSchema = z
  .object({
    type: z.enum(["DELAY", "BREAKDOWN", "ROAD_CLOSURE", "CANCELLATION_REQUEST"], { message: "Tür seçin." }),
    delayMinutes: z.number().int().min(1, "1–600 dk arası girin.").max(600, "1–600 dk arası girin.").nullable().optional(),
    note: z.string().trim().min(1, "Not zorunludur.").max(500, "En fazla 500 karakter."),
  })
  .superRefine((v, ctx) => {
    if (v.type === "DELAY" && (v.delayMinutes == null || Number.isNaN(v.delayMinutes))) {
      ctx.addIssue({ code: "custom", path: ["delayMinutes"], message: "Gecikme süresi zorunludur (1–600 dk)." });
    }
  });

export const correctionRequestSchema = z.object({
  requestedStatus: z.enum(["NONE", "BOARDED", "DROPPED_OFF", "ABSENT"], { message: "Olması gereken durumu seçin." }),
  reason: z.string().trim().min(1, "Gerekçe zorunludur.").max(500, "En fazla 500 karakter."),
});
