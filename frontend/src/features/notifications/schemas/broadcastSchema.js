import { z } from "zod";

export const BROADCAST_ROLES = ["PARENT", "STUDENT", "DRIVER", "HOSTESS", "ADMIN"];
export const TARGET_TYPES = ["ROLE", "PARENTS", "STUDENTS"];

/** Toplu bildirim formu: hedef türüne göre rol veya en az bir alıcı zorunlu; başlık ≤ 200. */
export const broadcastSchema = z
  .object({
    target: z.enum(TARGET_TYPES),
    targetRole: z.string(),
    parentIds: z.array(z.number()),
    studentIds: z.array(z.number()),
    title: z.string().trim().min(1, "Başlık zorunludur.").max(200, "Başlık en fazla 200 karakter olabilir."),
    body: z.string().trim().min(1, "Mesaj zorunludur.").max(4000, "Mesaj en fazla 4000 karakter olabilir."),
  })
  .superRefine((v, ctx) => {
    if (v.target === "ROLE" && !BROADCAST_ROLES.includes(v.targetRole)) {
      ctx.addIssue({ code: "custom", path: ["targetRole"], message: "Hedef rolü seçin." });
    }
    if (v.target === "PARENTS" && v.parentIds.length === 0) {
      ctx.addIssue({ code: "custom", path: ["parentIds"], message: "En az bir veli seçin." });
    }
    if (v.target === "STUDENTS" && v.studentIds.length === 0) {
      ctx.addIssue({ code: "custom", path: ["studentIds"], message: "En az bir öğrenci seçin." });
    }
  });

/** API gövdesi: yalnız seçili hedef türü gönderilir (API birden fazlasını birleştirir). */
export function toBroadcastBody(v) {
  const body = { title: v.title, body: v.body };
  if (v.target === "ROLE") body.targetRole = v.targetRole;
  if (v.target === "PARENTS") body.parentIds = v.parentIds;
  if (v.target === "STUDENTS") body.studentIds = v.studentIds;
  return body;
}
