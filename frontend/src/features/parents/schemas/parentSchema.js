import { z } from "zod";

const tcNo = z
  .string()
  .trim()
  .refine((v) => v === "" || /^\d{11}$/.test(v), "T.C. kimlik numarası 11 haneli olmalıdır.");

const fullName = z.string().trim().min(1, "Ad soyad zorunludur.").max(150, "Ad soyad en fazla 150 karakter olabilir.");
const phone = z.string().trim().max(20, "Telefon en fazla 20 karakter olabilir.");

/**
 * ParentCreateRequest (API ref §4.5): userId*, fullName*, phone?, tcNo? (11 hane).
 * accountMode "existing": mevcut PARENT hesabı seçilir; "new": önce POST /users (role=PARENT) ile
 * hesap açılır, ardından profil (POST /parents) oluşturulur — API.md §2 iki aşamalı akış.
 */
export const parentSchema = z
  .object({
    accountMode: z.enum(["existing", "new"]),
    userId: z.string(),
    email: z.string().trim(),
    fullName,
    phone,
    tcNo,
  })
  .superRefine((v, ctx) => {
    if (v.accountMode === "existing" && !v.userId) ctx.addIssue({ code: "custom", path: ["userId"], message: "Kullanıcı hesabı seçin." });
    if (v.accountMode === "new") {
      if (!v.email) ctx.addIssue({ code: "custom", path: ["email"], message: "E-posta zorunludur." });
      else if (!z.email().safeParse(v.email).success) ctx.addIssue({ code: "custom", path: ["email"], message: "Geçerli bir e-posta adresi girin." });
    }
  });

/** Güncelleme (PATCH /parents/{id}): userId değişmez. */
export const parentUpdateSchema = z.object({ fullName, phone, tcNo });
