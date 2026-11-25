"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { stopChangeRequestsService } from "@/features/stop-change-requests/service/stopChangeRequestsService";
import { toList } from "@/lib/http/page";
import { stopChangeKeys } from "./keys";

/** Velinin kendi açtığı durak değişikliği talepleri (yeni → eski). */
export function useMyStopChangeRequests() {
  return useQuery({
    queryKey: stopChangeKeys.mine(),
    queryFn: async () => toList(await stopChangeRequestsService.list({ size: 50, sort: "createdAt,desc" })),
    placeholderData: keepPreviousData,
  });
}

/** Seçilebilir duraklar. studentId VE direction zorunlu (eksikse backend 500 döner) — ikisi yokken istek atılmaz. */
export function useStopOptions(studentId, direction) {
  return useQuery({
    queryKey: stopChangeKeys.options(studentId, direction),
    queryFn: () => stopChangeRequestsService.options(studentId, direction),
    enabled: Boolean(studentId) && Boolean(direction),
    staleTime: 60_000,
  });
}
