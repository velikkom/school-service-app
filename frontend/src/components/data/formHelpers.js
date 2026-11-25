import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { errorMessage } from "@/lib/http/apiError";

/**
 * Form gönderim hatası: 400 `fieldErrors` → RHF alanlarına; diğerleri (409 vb.) için dialog içinde
 * gösterilecek metin döner. Alan hatası uygulandıysa genel bir uyarı metni döner.
 */
export function formErrorText(err, setError, fallback = "Kayıt yapılamadı.") {
  if (applyServerFieldErrors(err, setError)) return "Bazı alanlar geçersiz. İşaretli alanları düzeltin.";
  return errorMessage(err, fallback);
}

/** 1250 | "1250,5" | "1250.50" → "1250.50" biçimine yakın string (API sayı/string kabul eder). */
export function normalizeMoneyInput(value) {
  if (value === null || value === undefined) return "";
  return String(value).trim().replace(/\s/g, "").replace(",", ".");
}

/** Para alanı kuralı: pozitif, en çok 2 ondalık. */
export const MONEY_RE = /^\d+(\.\d{1,2})?$/;
