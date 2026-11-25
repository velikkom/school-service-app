import { z } from "zod";

/** SchoolCreateRequest (API ref §4.6): name* (benzersiz, 409), address?, phone?. Güncelleme + isActive. */
export const schoolSchema = z.object({
  name: z.string().trim().min(1, "Okul adı zorunludur.").max(200, "Okul adı en fazla 200 karakter olabilir."),
  address: z.string().trim().max(255, "Adres en fazla 255 karakter olabilir."),
  phone: z.string().trim().max(20, "Telefon en fazla 20 karakter olabilir."),
  isActive: z.boolean(),
});

export const SCHOOL_DEFAULTS = { name: "", address: "", phone: "", isActive: true };
