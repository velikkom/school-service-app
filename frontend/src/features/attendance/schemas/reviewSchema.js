import { z } from "zod";

/** Veli yoklama inceleme talebi (kind=REVIEW): hedef durum isteğe bağlı, açıklama zorunlu (≤500). */
export const reviewSchema = z.object({
  requestedStatus: z.enum(["NONE", "BOARDED", "DROPPED_OFF", "ABSENT"]).nullable().optional(),
  reason: z.string().trim().min(1, "Açıklama zorunludur.").max(500, "Açıklama en fazla 500 karakter olabilir."),
});
