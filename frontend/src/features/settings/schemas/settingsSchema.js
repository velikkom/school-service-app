import { z } from "zod";

const normalize = (v) => v.replace(/\s/g, "").replace(",", ".");
const isMoney = (v) => /^\d+(\.\d{1,2})?$/.test(v);

/**
 * Kurum ayarları formu. `original` mevcut ayardır: varsayılan aylık ücret bir kez tanımlandıysa
 * boşaltılamaz (API'de "boşalt" semantiği yok — null = değişmez).
 */
export function makeSettingsSchema(original) {
  const hadFee = original?.defaultMonthlyFee != null && original.defaultMonthlyFee !== "";
  return z.object({
    name: z.string().trim().min(1, "Kurum adı zorunludur.").max(200, "Kurum adı en fazla 200 karakter olabilir."),
    address: z.string().trim().max(255, "Adres en fazla 255 karakter olabilir."),
    phone: z.string().trim().max(20, "Telefon en fazla 20 karakter olabilir."),
    reportHeaderNote: z.string().trim().max(255, "Belge notu en fazla 255 karakter olabilir."),
    defaultMonthlyFee: z
      .string()
      .trim()
      .transform(normalize)
      .refine((v) => v !== "" || !hadFee, "Varsayılan ücret boşaltılamaz; yeni bir tutar girin.")
      .refine((v) => v === "" || isMoney(v), "Geçerli bir tutar girin (ör. 1250,00).")
      .refine((v) => v === "" || Number(v) > 0, "Tutar 0'dan büyük olmalıdır."),
  });
}

const TEXT_FIELDS = ["name", "address", "phone", "reportHeaderNote"];

/** API yanıtı → form değerleri. */
export function settingsToForm(s) {
  return {
    name: s?.name ?? "",
    address: s?.address ?? "",
    phone: s?.phone ?? "",
    reportHeaderNote: s?.reportHeaderNote ?? "",
    defaultMonthlyFee: s?.defaultMonthlyFee != null ? String(s.defaultMonthlyFee).replace(".", ",") : "",
  };
}

/**
 * Yarı-kısmi PATCH gövdesi: yalnız değişen alanlar gönderilir. Metin alanında boş string = temizle
 * (name hariç, şema engeller); ücret sayısal karşılaştırılır, boşsa gönderilmez.
 */
export function buildSettingsPatch(values, original) {
  const patch = {};
  for (const key of TEXT_FIELDS) {
    if ((values[key] ?? "") !== (original?.[key] ?? "")) patch[key] = values[key] ?? "";
  }
  const fee = values.defaultMonthlyFee;
  const before = original?.defaultMonthlyFee;
  if (fee !== "" && (before == null || Number(fee) !== Number(before))) patch.defaultMonthlyFee = fee;
  return patch;
}
