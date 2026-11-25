"use client";

import { ApiError } from "@/lib/http/apiError";

/**
 * `Content-Disposition` başlığından dosya adını çıkarır. RFC 6266 `filename*=UTF-8''...` biçimi
 * önceliklidir, yoksa `filename="..."` / `filename=...` okunur.
 * @param {string | null | undefined} header
 * @param {string} [fallback]
 * @returns {string}
 */
export function parseContentDispositionFilename(header, fallback = "belge") {
  if (!header || typeof header !== "string") return fallback;

  const extended = /filename\*\s*=\s*([^']*)'[^']*'([^;]+)/i.exec(header);
  if (extended) {
    try {
      return decodeURIComponent(extended[2].trim().replace(/^"|"$/g, ""));
    } catch {
      // bozuk yüzde kodlaması — düz filename'e düş
    }
  }

  const quoted = /filename\s*=\s*"([^"]+)"/i.exec(header);
  if (quoted) return quoted[1];

  const bare = /filename\s*=\s*([^;]+)/i.exec(header);
  if (bare) return bare[1].trim();

  return fallback;
}

/**
 * BFF üzerinden (`/api{path}`) bir dosyayı indirir: blob olarak alır, dosya adını
 * `Content-Disposition`'dan okur ve geçici bir `<a download>` ile tarayıcı indirmesini başlatır.
 * GET isteği olduğu için CSRF başlığı gerekmez (lib/http.js ile aynı kural).
 * Hata durumunda backend'in ApiError gövdesiyle bir `ApiError` fırlatır — çağıran taraf toast gösterir.
 * @param {string} path örn. "/payments/12/receipt"
 * @param {string} [fallbackName]
 * @returns {Promise<string>} indirilen dosyanın adı
 */
export async function downloadFile(path, fallbackName = "belge") {
  const res = await fetch(`/api${path}`, { method: "GET", credentials: "same-origin" });

  if (!res.ok) {
    let data = null;
    try {
      data = await res.json();
    } catch {
      data = null;
    }
    throw new ApiError(
      data ?? {
        status: res.status,
        error: "UNKNOWN_ERROR",
        message: "Belge indirilemedi.",
        path,
      }
    );
  }

  const blob = await res.blob();
  const filename = parseContentDispositionFilename(
    res.headers.get("content-disposition"),
    fallbackName
  );

  const url = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    anchor.rel = "noopener";
    anchor.style.display = "none";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  } finally {
    // Tıklama senkron tetiklenir; URL'yi bir sonraki tick'te bırakmak bazı tarayıcılarda
    // indirmenin iptal olmasını önler.
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
  return filename;
}
