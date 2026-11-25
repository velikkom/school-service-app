import { z } from "zod";

/** StudentCreate/UpdateRequest (API ref §4.4): fullName*, birthDate*, tcNo? (11 hane), schoolId?, grade?. */
export const studentSchema = z.object({
  fullName: z.string().trim().min(1, "Ad soyad zorunludur.").max(150, "Ad soyad en fazla 150 karakter olabilir."),
  birthDate: z
    .string()
    .trim()
    .min(1, "Doğum tarihi zorunludur.")
    .refine((v) => /^\d{4}-\d{2}-\d{2}$/.test(v), "Geçerli bir tarih girin."),
  tcNo: z
    .string()
    .trim()
    .refine((v) => v === "" || /^\d{11}$/.test(v), "T.C. kimlik numarası 11 haneli olmalıdır."),
  schoolId: z.string(),
  grade: z.string().trim().max(50, "Sınıf en fazla 50 karakter olabilir."),
});

export const STUDENT_DEFAULTS = { fullName: "", birthDate: "", tcNo: "", schoolId: "", grade: "" };

export function studentToForm(s) {
  if (!s) return STUDENT_DEFAULTS;
  return {
    fullName: s.fullName ?? "",
    birthDate: s.birthDate ?? "",
    tcNo: s.tcNo ?? "",
    schoolId: s.schoolId != null ? String(s.schoolId) : "",
    grade: s.grade ?? "",
  };
}

export function studentToBody(v) {
  return {
    fullName: v.fullName.trim(),
    birthDate: v.birthDate,
    tcNo: v.tcNo || undefined,
    schoolId: v.schoolId ? Number(v.schoolId) : undefined,
    grade: v.grade || undefined,
  };
}

/** Veli–öğrenci bağı yakınlık seçenekleri ortak etiketlerden gelir. */
export { RELATION_OPTIONS, relationLabel } from "@/lib/formatting/labels";

/** POST /parent-student-links: parentId*, studentId*, relationType*. Tekrar eden çift 409. */
export const linkSchema = z.object({
  parentId: z.string().min(1, "Veli seçin."),
  studentId: z.string().min(1, "Öğrenci seçin."),
  relationType: z.string().trim().min(1, "Yakınlık seçin."),
});

/**
 * Sprint 3 — STUDENT hesabı bağlama (API §3 "Öğrenci hesabı bağı"). accountMode "new": önce
 * POST /users (role=STUDENT), ardından PUT /students/{id}/account.
 */
export const studentAccountSchema = z
  .object({
    accountMode: z.enum(["existing", "new"]),
    userId: z.string(),
    fullName: z.string().trim(),
    email: z.string().trim(),
    phone: z.string().trim().max(20, "Telefon en fazla 20 karakter olabilir."),
  })
  .superRefine((v, ctx) => {
    if (v.accountMode === "existing" && !v.userId) ctx.addIssue({ code: "custom", path: ["userId"], message: "Öğrenci hesabı seçin." });
    if (v.accountMode === "new") {
      if (!v.fullName) ctx.addIssue({ code: "custom", path: ["fullName"], message: "Ad soyad zorunludur." });
      if (!v.email) ctx.addIssue({ code: "custom", path: ["email"], message: "E-posta zorunludur." });
      else if (!z.email().safeParse(v.email).success) ctx.addIssue({ code: "custom", path: ["email"], message: "Geçerli bir e-posta adresi girin." });
    }
  });
