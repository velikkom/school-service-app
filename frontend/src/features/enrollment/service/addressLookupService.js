/**
 * Türkiye il/ilçe/mahalle referans verisi — api.turkiyeapi.dev (anahtarsız, CORS açık, üçüncü taraf).
 * Bu bizim backend'imiz değildir: BFF'ten geçmez, tarayıcıdan doğrudan okunur (karar: docs/PROGRESS.md,
 * 2026-10-25). Yalnız öğrenci ekleme sihirbazının adres adımındaki kademeli seçimler için kullanılır;
 * servis hata verirse sihirbaz serbest metne düşer. Backend'e her zaman seçilen öğenin ADI gider.
 *
 * Bilinen tuzak: iç içe `/provinces/{id}/districts` ucu `sort` parametresini kabul etmez (400) —
 * sıralama istemcide yapılır. Mahalleler ilçe → belediye (municipalityId) → mahalle zinciriyle gelir.
 */
const BASE_URL = "https://api.turkiyeapi.dev/v2";

async function getJson(url) {
  const res = await fetch(url, { credentials: "omit" });
  if (!res.ok) throw new Error(`Adres servisi yanıt vermedi (HTTP ${res.status}).`);
  const body = await res.json();
  return Array.isArray(body?.data) ? body.data : [];
}

const byNameTR = (a, b) => String(a?.name ?? "").localeCompare(String(b?.name ?? ""), "tr");

export const addressLookupService = {
  provinces: async () => [...(await getJson(`${BASE_URL}/provinces?fields=id,name&sort=name`))].sort(byNameTR),
  districts: async (provinceId) => [...(await getJson(`${BASE_URL}/provinces/${encodeURIComponent(provinceId)}/districts?fields=id,name`))].sort(byNameTR),
  neighborhoods: async (districtId) => {
    const municipalities = await getJson(`${BASE_URL}/districts/${encodeURIComponent(districtId)}/municipalities?fields=id,name`);
    const municipalityId = municipalities[0]?.id;
    if (!municipalityId) return [];
    const list = await getJson(`${BASE_URL}/neighborhoods?municipalityId=${encodeURIComponent(municipalityId)}&fields=id,name&sort=name&limit=500`);
    return [...list].sort(byNameTR);
  },
};
