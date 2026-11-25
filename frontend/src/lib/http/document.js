import { ApiError } from "@/lib/http/apiError";

/**
 * Makbuz/ekstre gibi HTML belgeleri (API §27) BFF'ten alır. Backend `attachment` döndürdüğü için
 * doğrudan bağlantı indirir; "Aç / yazdır" için içerik blob olarak alınıp aynı origin'de açılır.
 * @param {string} path "/payments/12/receipt"
 * @returns {Promise<{ html: string, filename: string }>}
 */
export async function fetchHtmlDocument(path) {
  const res = await fetch(`/api${path}`, { credentials: "same-origin", cache: "no-store" });
  if (!res.ok) {
    let data = null;
    try {
      data = await res.json();
    } catch {
      data = null;
    }
    throw new ApiError(data ?? { status: res.status, error: "UNKNOWN_ERROR", message: "Belge açılamadı.", path });
  }
  const html = await res.text();
  const cd = res.headers.get("content-disposition") || "";
  const m = /filename="?([^";]+)"?/i.exec(cd);
  return { html, filename: m ? m[1] : "belge.html" };
}

/** Belgeyi yeni sekmede açar (yazdırma tarayıcıdan). Açılır pencere engellenirse indirir. */
export async function openHtmlDocument(path) {
  const win = window.open("", "_blank");
  const { html, filename } = await fetchHtmlDocument(path);
  const url = URL.createObjectURL(new Blob([html], { type: "text/html;charset=utf-8" }));
  if (win) {
    win.location.href = url;
  } else {
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
