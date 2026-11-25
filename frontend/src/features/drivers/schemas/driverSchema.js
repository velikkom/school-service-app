import { z } from "zod";

const licenseNo = z.string().trim().min(1, "Ehliyet numarası zorunludur.").max(50, "Ehliyet numarası en fazla 50 karakter olabilir.");

/**
 * DriverCreateRequest (API ref §4.7): userId*, licenseNo*.
 * accountMode "new": önce POST /users (role=DRIVER) ile hesap, ardından POST /drivers.
 */
export const driverSchema = z
  .object({
    accountMode: z.enum(["existing", "new"]),
    userId: z.string(),
    fullName: z.string().trim(),
    email: z.string().trim(),
    phone: z.string().trim().max(20, "Telefon en fazla 20 karakter olabilir."),
    licenseNo,
  })
  .superRefine((v, ctx) => {
    if (v.accountMode === "existing" && !v.userId) ctx.addIssue({ code: "custom", path: ["userId"], message: "Kullanıcı hesabı seçin." });
    if (v.accountMode === "new") {
      if (!v.fullName) ctx.addIssue({ code: "custom", path: ["fullName"], message: "Ad soyad zorunludur." });
      if (!v.email) ctx.addIssue({ code: "custom", path: ["email"], message: "E-posta zorunludur." });
      else if (!z.email().safeParse(v.email).success) ctx.addIssue({ code: "custom", path: ["email"], message: "Geçerli bir e-posta adresi girin." });
    }
  });

/** DriverUpdateRequest: licenseNo?, isActive?. */
export const driverUpdateSchema = z.object({ licenseNo, isActive: z.boolean() });
