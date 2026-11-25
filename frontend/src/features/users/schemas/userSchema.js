import { z } from "zod";

export const ROLE_VALUES = ["ADMIN", "DRIVER", "HOSTESS", "PARENT", "STUDENT"];
/** Oluşturma sonrası /users/{id}/role ile profili sunucuda "ensure" edilen roller (API.md §2). */
export const PROFILE_ROLES = ["DRIVER", "HOSTESS", "PARENT"];

const fullName = z.string().trim().min(1, "Ad soyad zorunludur.").max(150, "Ad soyad en fazla 150 karakter olabilir.");
const phone = z.string().trim().max(20, "Telefon en fazla 20 karakter olabilir.");

/**
 * UserCreateRequest (API ref §4.3): fullName*, email*, phone?, role*, password? (min 8; boşsa sunucu üretir).
 * licenseNo: DRIVER için ardından yapılan /users/{id}/role çağrısında zorunlu.
 */
export const userCreateSchema = z
  .object({
    fullName,
    email: z.string().trim().min(1, "E-posta zorunludur.").email("Geçerli bir e-posta adresi girin."),
    phone,
    role: z.enum(ROLE_VALUES, { message: "Rol seçin." }),
    password: z.string().refine((v) => v === "" || v.length >= 8, "Şifre en az 8 karakter olmalıdır."),
    licenseNo: z.string().trim(),
  })
  .superRefine((v, ctx) => {
    if (v.role === "DRIVER" && !v.licenseNo) ctx.addIssue({ code: "custom", path: ["licenseNo"], message: "Şoför için ehliyet numarası zorunludur." });
  });

/** PATCH /users/{id}: fullName?, phone? (rol ve aktiflik ayrı işlemler). */
export const userUpdateSchema = z.object({ fullName, phone });

/**
 * POST /users/{id}/role: role*, licenseNo? — DRIVER hedefinde kullanıcının şoför profili yoksa zorunlu
 * (sunucu 400 döner). Profil varsa mevcut profil yeniden kullanılır.
 */
export function roleChangeSchema({ hasDriverProfile = false } = {}) {
  return z
    .object({
      role: z.enum(ROLE_VALUES, { message: "Rol seçin." }),
      licenseNo: z.string().trim(),
    })
    .superRefine((v, ctx) => {
      if (v.role === "DRIVER" && !hasDriverProfile && !v.licenseNo) {
        ctx.addIssue({ code: "custom", path: ["licenseNo"], message: "Şoför için ehliyet numarası zorunludur." });
      }
    });
}
