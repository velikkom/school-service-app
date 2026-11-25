import { z } from "zod";
import { MONEY_RE, normalizeMoneyInput } from "@/components/data/formHelpers";

export const PAYMENT_METHOD_VALUES = ["CASH", "BANK_TRANSFER", "CARD", "OTHER"];

/** PaymentCreateRequest (API ref §4.13): studentId*, amount* (>0), method*, note?. Idempotency-Key başlıkta. */
export const paymentSchema = z.object({
  studentId: z.string().min(1, "Öğrenci seçin."),
  amount: z
    .string()
    .transform(normalizeMoneyInput)
    .refine((v) => v !== "", "Tutar zorunludur.")
    .refine((v) => v === "" || (MONEY_RE.test(v) && Number(v) > 0), "Geçerli bir tutar girin (örn. 1250,00)."),
  method: z.enum(PAYMENT_METHOD_VALUES, { message: "Ödeme yöntemi seçin." }),
  note: z.string().trim().max(500, "Not en fazla 500 karakter olabilir."),
});
