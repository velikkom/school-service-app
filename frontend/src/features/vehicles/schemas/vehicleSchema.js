import { z } from "zod";

/** Plaka: boşluklar tekleştirilir, büyük harf (Türkçe). Sunucu da normalize eder; benzersizdir (409). */
export function normalizePlate(v) {
  return String(v ?? "")
    .trim()
    .replace(/\s+/g, " ")
    .toLocaleUpperCase("tr");
}

/** VehicleCreateRequest (API ref §4.7): plate*, capacity* (>0 tam sayı), model?. Güncelleme + isActive. */
export const vehicleSchema = z.object({
  plate: z
    .string()
    .trim()
    .min(1, "Plaka zorunludur.")
    .max(20, "Plaka en fazla 20 karakter olabilir.")
    .refine((v) => /^[0-9A-Za-zÇĞİÖŞÜçğıöşü ]+$/.test(v), "Plaka yalnızca harf, rakam ve boşluk içerebilir."),
  capacity: z
    .string()
    .trim()
    .min(1, "Kapasite zorunludur.")
    .refine((v) => /^\d+$/.test(v) && Number(v) > 0, "Kapasite pozitif bir tam sayı olmalıdır."),
  model: z.string().trim().max(100, "Model en fazla 100 karakter olabilir."),
  isActive: z.boolean(),
});

export const VEHICLE_DEFAULTS = { plate: "", capacity: "", model: "", isActive: true };
