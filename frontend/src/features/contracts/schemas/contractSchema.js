import { z } from "zod";
import { MONEY_RE, normalizeMoneyInput } from "@/components/data/formHelpers";

const money = (req) =>
  z
    .string()
    .transform(normalizeMoneyInput)
    .refine((v) => v !== "", req)
    .refine((v) => v === "" || (MONEY_RE.test(v) && Number(v) > 0), "Geçerli bir tutar girin (örn. 1500,00).");

const optionalMoney = z
  .string()
  .transform(normalizeMoneyInput)
  .refine((v) => v === "" || MONEY_RE.test(v), "Geçerli bir tutar girin (örn. 150,00).");

/** ContractCreateRequest (API ref §4.13): studentId*, monthlyFee* (>0), startDate*, endDate?, discountAmount? (≥0). */
export const contractCreateSchema = z
  .object({
    studentId: z.string().min(1, "Öğrenci seçin."),
    monthlyFee: money("Aylık ücret zorunludur."),
    discountAmount: optionalMoney,
    startDate: z.string().trim().min(1, "Başlangıç tarihi zorunludur."),
    endDate: z.string().trim(),
  })
  .refine((v) => !v.endDate || v.endDate >= v.startDate, { path: ["endDate"], message: "Bitiş tarihi başlangıçtan önce olamaz." });

/** ContractUpdateRequest: monthlyFee?, discountAmount?, endDate? (yalnız ACTIVE sözleşme; aksi 409). */
export const contractUpdateSchema = z.object({
  monthlyFee: money("Aylık ücret zorunludur."),
  discountAmount: optionalMoney,
  endDate: z.string().trim(),
});
