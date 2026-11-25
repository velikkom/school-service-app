import { z } from "zod";
import { todayISO } from "@/lib/formatting/date";

/**
 * Veli durak değişikliği talebi (POST /stop-change-requests). Yürürlük tarihi bugün veya sonrası
 * (Europe/Istanbul); backend geçmiş tarihi 400 ile reddeder. `today` testte sabitlenebilir.
 */
export function makeStopChangeSchema(today = todayISO()) {
  return z.object({
    studentId: z.number({ message: "Öğrenci seçin." }).int().positive("Öğrenci seçin."),
    direction: z.enum(["MORNING", "AFTERNOON"], { message: "Yön seçin." }),
    requestedStopId: z.number({ message: "Yeni durağı seçin." }).int().positive("Yeni durağı seçin."),
    effectiveDate: z
      .string()
      .regex(/^\d{4}-\d{2}-\d{2}$/, "Geçerlilik tarihi seçin.")
      .refine((v) => v >= today, "Tarih bugünden önce olamaz."),
    reason: z.string().trim().max(500, "Açıklama en fazla 500 karakter olabilir.").optional().or(z.literal("")),
  });
}
