import { z } from "zod";
import { todayISO } from "@/lib/formatting/date";

/**
 * POST /parents/me/student-enrollments gövdesi ile birebir aynı iç içe şekil (RHF alan adları
 * `student.tcNo`, `address.city` …) — böylece 400 `fieldErrors[].field` doğrudan forma uygulanır.
 * schoolId formda string tutulur (Dropdown), gönderimde sayıya çevrilir.
 */
export const RELATION_TYPES = ["Anne", "Baba", "Vasi", "Diğer"];

const req = (msg) => z.string({ message: msg }).trim().min(1, msg);

export const enrollmentSchema = z.object({
  schoolId: z.union([z.number(), z.string()], { message: "Okul seçin." }).refine((v) => v !== "" && v != null, "Okul seçin."),
  relationType: req("Yakınlığınızı seçin."),
  parentPhone: req("Telefon zorunludur.")
    .refine((v) => v.replace(/\D/g, "").length >= 10, "Telefon en az 10 haneli olmalıdır.")
    .refine((v) => v.length <= 20, "Telefon en fazla 20 karakter olabilir."),
  student: z.object({
    fullName: req("Ad soyad zorunludur.").max(150, "Ad soyad en fazla 150 karakter olabilir."),
    birthDate: req("Doğum tarihi zorunludur.").refine((v) => /^\d{4}-\d{2}-\d{2}$/.test(v) && v <= todayISO(), "Geçerli bir doğum tarihi girin."),
    tcNo: req("T.C. kimlik numarası zorunludur.").refine((v) => /^\d{11}$/.test(v), "T.C. kimlik numarası 11 hane olmalıdır."),
    grade: req("Sınıf zorunludur.").max(30, "Sınıf en fazla 30 karakter olabilir."),
    gender: z.enum(["MALE", "FEMALE"], { message: "Cinsiyet seçin." }),
    healthNote: z.string().trim().max(500, "Sağlık notu en fazla 500 karakter olabilir.").optional().or(z.literal("")),
  }),
  address: z.object({
    city: req("İl zorunludur."),
    district: req("İlçe zorunludur."),
    neighborhood: req("Mahalle zorunludur."),
    addressLine: req("Açık adres zorunludur.").max(500, "Açık adres en fazla 500 karakter olabilir."),
  }),
  contractAccepted: z.boolean().refine((v) => v === true, "Devam etmek için sözleşmeyi onaylayın."),
});

/** Sihirbaz adımları ve her adımda doğrulanan alanlar (okul → öğrenci → adres → sözleşme). */
export const WIZARD_STEPS = [
  { key: "school", label: "Okul", fields: ["schoolId"] },
  {
    key: "student",
    label: "Öğrenci",
    fields: ["student.fullName", "student.birthDate", "student.tcNo", "student.grade", "student.gender", "student.healthNote", "relationType"],
  },
  { key: "address", label: "Adres", fields: ["address.city", "address.district", "address.neighborhood", "address.addressLine", "parentPhone"] },
  { key: "contract", label: "Sözleşme", fields: ["contractAccepted"] },
];

/** Sunucu alan adının bulunduğu adım (400 sonrası o adıma dönmek için). */
export function stepOfField(field) {
  const i = WIZARD_STEPS.findIndex((s) => s.fields.includes(field));
  return i < 0 ? null : i;
}

export const emptyEnrollment = () => ({
  schoolId: "",
  relationType: "",
  parentPhone: "",
  student: { fullName: "", birthDate: "", tcNo: "", grade: "", gender: "", healthNote: "" },
  address: { city: "", district: "", neighborhood: "", addressLine: "" },
  contractAccepted: false,
});

/** Form değerleri → istek gövdesi. */
export function toEnrollmentPayload(v) {
  return {
    schoolId: Number(v.schoolId),
    relationType: v.relationType.trim(),
    parentPhone: v.parentPhone.trim(),
    student: {
      fullName: v.student.fullName.trim(),
      birthDate: v.student.birthDate,
      tcNo: v.student.tcNo.trim(),
      grade: v.student.grade.trim(),
      gender: v.student.gender,
      healthNote: v.student.healthNote?.trim() || undefined,
    },
    address: {
      city: v.address.city.trim(),
      district: v.address.district.trim(),
      neighborhood: v.address.neighborhood.trim(),
      addressLine: v.address.addressLine.trim(),
    },
    contractAccepted: v.contractAccepted === true,
  };
}
