import { z } from "zod";

export const APPROVABLE_ROLES = ["PARENT", "DRIVER", "HOSTESS", "STUDENT", "ADMIN"];

/** "Rol ver" formu: rol zorunlu; DRIVER'da ehliyet no zorunlu; telefon isteğe bağlı (≤ 20). */
export const registrationApproveSchema = z
  .object({
    role: z.enum(APPROVABLE_ROLES, { error: "Rol seçin." }),
    phone: z.string().trim().max(20, "Telefon en fazla 20 karakter olabilir."),
    licenseNo: z.string().trim().max(50, "Ehliyet no en fazla 50 karakter olabilir."),
  })
  .superRefine((v, ctx) => {
    if (v.role === "DRIVER" && !v.licenseNo) {
      ctx.addIssue({ code: "custom", path: ["licenseNo"], message: "Şoför rolünde ehliyet no zorunludur." });
    }
  });

/** API gövdesi: boş alanlar gönderilmez; licenseNo yalnız DRIVER'da. */
export function toRegistrationApproveBody(values) {
  const body = { role: values.role };
  if (values.phone) body.phone = values.phone;
  if (values.role === "DRIVER") body.licenseNo = values.licenseNo;
  return body;
}
