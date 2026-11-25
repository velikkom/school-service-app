/**
 * docs/API.md hata şeması: `fieldErrors: [{ field, message }]`. Bu yardımcı, sunucudan dönen alan
 * bazlı validasyon hatalarını React Hook Form'un `setError`'ına uygular — böylece kullanıcı hangi
 * alanın neden reddedildiğini formun üzerinde görür (genel bir toast yerine).
 * @param {import("@/lib/http/apiError").ApiError} err
 * @param {(name: string, error: { type: string, message: string }) => void} setError
 * @returns {boolean} en az bir alan hatası uygulandıysa true
 */
export function applyServerFieldErrors(err, setError) {
  const fieldErrors = err?.fieldErrors;
  if (!Array.isArray(fieldErrors) || fieldErrors.length === 0) return false;
  for (const fieldError of fieldErrors) {
    if (!fieldError?.field) continue;
    setError(fieldError.field, { type: "server", message: fieldError.message });
  }
  return true;
}
