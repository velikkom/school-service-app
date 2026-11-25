import { z } from "zod";

/** "Servise binmeyecek" bildirimi: not isteğe bağlı, en fazla 500 karakter (API §22). */
export const absenceNoticeSchema = z.object({
  note: z.string().trim().max(500, "Not en fazla 500 karakter olabilir.").optional().or(z.literal("")),
});
