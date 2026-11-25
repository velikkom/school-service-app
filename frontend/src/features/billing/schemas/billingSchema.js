import { z } from "zod";

/** POST /billing/run { period: "YYYY-MM" }. */
export const billingRunSchema = z.object({
  period: z
    .string()
    .trim()
    .min(1, "Dönem zorunludur.")
    .refine((v) => /^\d{4}-(0[1-9]|1[0-2])$/.test(v), "Dönem YYYY-AA biçiminde olmalıdır."),
});
