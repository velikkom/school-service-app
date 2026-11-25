"use client";

import { useQuery } from "@tanstack/react-query";
import { trackingService } from "@/features/tracking/service/trackingService";

export const trackingKeys = {
  latest: (tripId) => ["tracking", "latest", Number(tripId)],
};

/**
 * Seferin son konumu. 404 = "henüz konum yok / sefer aktif değil" — hata değil, boş durum
 * (data: null). Yalnız aktif seferde 15 sn'de bir yenilenir.
 */
export function useLatestLocation(tripId, { enabled = true, live = true } = {}) {
  return useQuery({
    queryKey: trackingKeys.latest(tripId),
    enabled: Boolean(tripId) && enabled,
    refetchInterval: live ? 15_000 : false,
    queryFn: async () => {
      try {
        return await trackingService.latest(tripId);
      } catch (err) {
        if (err?.status === 404) return null;
        throw err;
      }
    },
  });
}
