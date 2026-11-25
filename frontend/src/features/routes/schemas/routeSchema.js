import { z } from "zod";

/** RouteCreateRequest (API ref §4.8): name*, direction*. Güncelleme + isActive. */
export const routeSchema = z.object({
  name: z.string().trim().min(1, "Güzergâh adı zorunludur.").max(150, "Güzergâh adı en fazla 150 karakter olabilir."),
  direction: z.enum(["MORNING", "AFTERNOON"], { message: "Yön seçin." }),
  isActive: z.boolean(),
});

/** StopCreateRequest: name*, sequenceNo* (>0), plannedTime* ("HH:mm"). Sıra no çakışması 409. */
export const stopSchema = z.object({
  name: z.string().trim().min(1, "Durak adı zorunludur.").max(150, "Durak adı en fazla 150 karakter olabilir."),
  sequenceNo: z
    .string()
    .trim()
    .min(1, "Sıra numarası zorunludur.")
    .refine((v) => /^\d+$/.test(v) && Number(v) > 0, "Sıra numarası pozitif bir tam sayı olmalıdır."),
  plannedTime: z
    .string()
    .trim()
    .min(1, "Planlanan saat zorunludur.")
    .refine((v) => /^\d{2}:\d{2}(:\d{2})?$/.test(v), "Saat SS:DD biçiminde olmalıdır."),
});
