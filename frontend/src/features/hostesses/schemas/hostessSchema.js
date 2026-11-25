import { z } from "zod";

/** HostessCreateRequest (API ref §4.7): userId*. accountMode "new": önce POST /users (role=HOSTESS). */
export const hostessSchema = z
  .object({
    accountMode: z.enum(["existing", "new"]),
    userId: z.string(),
    fullName: z.string().trim(),
    email: z.string().trim(),
    phone: z.string().trim().max(20, "Telefon en fazla 20 karakter olabilir."),
  })
  .superRefine((v, ctx) => {
    if (v.accountMode === "existing" && !v.userId) ctx.addIssue({ code: "custom", path: ["userId"], message: "Kullanıcı hesabı seçin." });
    if (v.accountMode === "new") {
      if (!v.fullName) ctx.addIssue({ code: "custom", path: ["fullName"], message: "Ad soyad zorunludur." });
      if (!v.email) ctx.addIssue({ code: "custom", path: ["email"], message: "E-posta zorunludur." });
      else if (!z.email().safeParse(v.email).success) ctx.addIssue({ code: "custom", path: ["email"], message: "Geçerli bir e-posta adresi girin." });
    }
  });
