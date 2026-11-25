import { z } from "zod";

/**
 * AssignmentCreateRequest (API ref §4.9): studentId*, routeId*, stopId*, direction*, validFrom*, validTo?.
 * Güncellemede studentId değişmez. Aynı öğrenci+yönde tarihleri çakışan aktif atama 409.
 */
export const assignmentSchema = z
  .object({
    studentId: z.string().min(1, "Öğrenci seçin."),
    direction: z.enum(["MORNING", "AFTERNOON"], { message: "Yön seçin." }),
    routeId: z.string().min(1, "Güzergâh seçin."),
    stopId: z.string().min(1, "Durak seçin."),
    validFrom: z.string().trim().min(1, "Başlangıç tarihi zorunludur."),
    validTo: z.string().trim(),
  })
  .refine((v) => !v.validTo || v.validTo >= v.validFrom, {
    path: ["validTo"],
    message: "Bitiş tarihi başlangıçtan önce olamaz.",
  });
