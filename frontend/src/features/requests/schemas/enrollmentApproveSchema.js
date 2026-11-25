import { z } from "zod";

const normalize = (v) => v.replace(/\s/g, "").replace(",", ".");
const isMoney = (v) => /^\d+(\.\d{1,2})?$/.test(v);
const isIsoDate = (v) => /^\d{4}-\d{2}-\d{2}$/.test(v);

/** "İncele ve onayla" formu: monthlyFee zorunlu (>0), discountAmount ≥ 0, startDate isteğe bağlı. */
export const enrollmentApproveSchema = z.object({
  monthlyFee: z
    .string()
    .trim()
    .min(1, "Aylık ücret zorunludur.")
    .transform(normalize)
    .refine(isMoney, "Geçerli bir tutar girin (ör. 1250,00).")
    .refine((v) => Number(v) > 0, "Aylık ücret 0'dan büyük olmalıdır."),
  discountAmount: z
    .string()
    .trim()
    .transform(normalize)
    .refine((v) => v === "" || isMoney(v), "Geçerli bir indirim tutarı girin."),
  startDate: z
    .string()
    .trim()
    .refine((v) => v === "" || isIsoDate(v), "Geçerli bir tarih seçin."),
});

/** Doğrulanmış form değerlerinden API gövdesi (boş alanlar gönderilmez). */
export function toApproveBody(values) {
  const body = { monthlyFee: values.monthlyFee };
  if (values.discountAmount) body.discountAmount = values.discountAmount;
  if (values.startDate) body.startDate = values.startDate;
  return body;
}

/** "1250.00" → "1250,00" (input için). */
export function moneyToInput(value) {
  if (value === null || value === undefined || value === "") return "";
  return String(value).replace(".", ",");
}
