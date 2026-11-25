/**
 * docs/API.md hata şemasının istemci karşılığı: { status, error, message, path, fieldErrors }.
 * 409'ların hepsi `error: "CONFLICT"` (alt kod yok); `CONCURRENT_CONFLICT` "tekrar dene" demektir.
 */
export class ApiError extends Error {
  constructor({ status, error, message, path, fieldErrors }) {
    super(message || "Beklenmeyen bir hata oluştu.");
    this.name = "ApiError";
    this.status = status;
    this.error = error;
    this.path = path;
    this.fieldErrors = fieldErrors ?? [];
  }
}

export const isNotFound = (err) => err?.status === 404;
export const isForbidden = (err) => err?.status === 403;
export const isConflict = (err) => err?.status === 409;

/** Kullanıcıya gösterilecek Türkçe hata metni. */
export function errorMessage(err, fallback = "İşlem tamamlanamadı.") {
  if (!err) return fallback;
  if (err.status === 409 && err.error === "CONCURRENT_CONFLICT") {
    return "Kayıt aynı anda başka biri tarafından değiştirildi. Sayfayı yenileyip tekrar deneyin.";
  }
  if (err.status === 403) return err.message || "Bu işlem için yetkiniz yok.";
  if (err.status >= 500 && err.status !== 502) return "Sunucuda beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.";
  return err.message || fallback;
}
