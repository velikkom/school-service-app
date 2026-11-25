/**
 * Liste endpoint'leri için ortak query-string üretici. `null`/`undefined`/boş string değerleri
 * ATAR (backend'e gönderilmez) — docs/API.md pagination/filtre parametreleri hepsi opsiyoneldir.
 * @param {Record<string, string | number | boolean | null | undefined>} params
 * @returns {string} örn. "?q=ali&page=0" veya boşsa ""
 */
export function toQueryString(params) {
  if (!params) return "";
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") continue;
    search.set(key, String(value));
  }
  const str = search.toString();
  return str ? `?${str}` : "";
}
