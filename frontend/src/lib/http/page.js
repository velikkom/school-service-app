/** Sayfalı (Spring Page) veya düz dizi yanıtı tek biçime indirir. */
export function toList(response) {
  if (Array.isArray(response)) return response;
  return response?.content ?? [];
}

export function pageMeta(response) {
  if (Array.isArray(response)) return { page: 0, size: response.length, totalElements: response.length, totalPages: 1 };
  return {
    page: response?.page ?? response?.number ?? 0,
    size: response?.size ?? 20,
    totalElements: response?.totalElements ?? 0,
    totalPages: response?.totalPages ?? 0,
  };
}
