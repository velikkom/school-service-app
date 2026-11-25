"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { attendanceCorrectionsService } from "@/features/attendance-corrections/service/attendanceCorrectionsService";

import { correctionKeys } from "./keys";

/** Sayfalı talep listesi (ADMIN: tümü, D/H/P: kendi açtıkları). */
export function useCorrectionList(params, { live = false } = {}) {
  return useQuery({
    queryKey: correctionKeys.list(params),
    queryFn: () => attendanceCorrectionsService.list(params),
    placeholderData: keepPreviousData,
    refetchInterval: live ? 60_000 : false,
  });
}
