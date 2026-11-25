const SENTINEL_ORIGIN = "http://ssp-local-sentinel.invalid";

/**
 * `from`/`returnTo` gibi istemciden gelen yönlendirme hedeflerini doğrular — SADECE güvenli,
 * yerel (same-origin, tek "/" ile başlayan, protokol/host İÇERMEYEN) adreslere izin verir.
 * Açık yönlendirme (open redirect) önlemi.
 *
 * Önce kontrol karakterleri (tab/CR/LF vb.) reddedilir — bazı URL/tarayıcı ayrıştırıcıları bunları
 * yok sayarak "/\t/evil.com" gibi bir girdiyi "//evil.com" gibi yorumlayabilir (ayrıştırıcı
 * farklılığı/"parser differential" saldırısı), oysa aşağıdaki saf string kontrolleri bunu
 * YAKALAYAMAZ. Sonra gerçek `URL` ayrıştırıcısıyla sabit bir sentinel origin'e göre çözülür ve
 * origin'in DEĞİŞMEDİĞİ doğrulanır — ama bu TEK BAŞINA YETERLİ DEĞİL: "/.//evil.com" gibi bir
 * girdi, nokta-segment normalizasyonu YÜZÜNDEN origin'i DEĞİŞTİRMEDEN (hâlâ sentinel'e göre
 * çözülür) `pathname`'i "//evil.com"a dönüştürebilir — çıktı bu fonksiyonun ÇAĞIRANLARI
 * tarafından (örn. `new URL(sanitizeLocalPath(...), gercekOrigin)`) AYRI bir temel URL'e göre
 * TEKRAR çözülüyor, ve o ikinci çözümlemede "//evil.com" protokol-göreli bir adres olarak
 * yorumlanıp GERÇEKTEN evil.com'a yönlendirirdi (gerçek Node `URL` ile doğrulandı). Bu yüzden
 * ÇIKTI da `//` veya `/\` ile BAŞLAMADIĞI ayrıca kontrol edilmeden döndürülmez.
 */
export function sanitizeLocalPath(path, fallback = "/dashboard") {
  if (typeof path !== "string" || path.length === 0) return fallback;
  for (let i = 0; i < path.length; i += 1) {
    const code = path.charCodeAt(i);
    if (code <= 0x1f || code === 0x7f) return fallback; // kontrol karakterleri (tab/CR/LF dahil)
  }
  if (!path.startsWith("/")) return fallback;
  if (path.startsWith("//")) return fallback; // protocol-relative (//evil.com)
  if (path.includes("://")) return fallback;
  if (path.startsWith("/\\")) return fallback;
  try {
    const resolved = new URL(path, SENTINEL_ORIGIN);
    if (resolved.origin !== SENTINEL_ORIGIN) return fallback;
    const result = `${resolved.pathname}${resolved.search}${resolved.hash}`;
    // Normalizasyon SONRASI çıktı da aynı protokol-göreli şekillere kaymamış olmalı (yukarıdaki
    // "/.//evil.com" örneği) — tek bir "/" ile başlamayan hiçbir sonuç döndürülmez.
    if (result.startsWith("//") || result.startsWith("/\\")) return fallback;
    return result;
  } catch {
    return fallback;
  }
}
