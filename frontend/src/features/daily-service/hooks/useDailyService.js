"use client";

import { useQuery } from "@tanstack/react-query";
import { dailyServiceService } from "@/features/daily-service/service/dailyServiceService";
import { dailyServiceKeys } from "./keys";

/** Öğrencinin seçili gündeki seferleri (önce MORNING, sonra AFTERNOON). Veli/öğrenci için 30 sn polling. */
export function useDailyService(studentId, date, { live = true } = {}) {
  return useQuery({
    queryKey: dailyServiceKeys.entry(studentId, date),
    queryFn: () => dailyServiceService.forStudent(studentId, date),
    enabled: Boolean(studentId) && Boolean(date),
    refetchInterval: live ? 30_000 : false,
    refetchOnWindowFocus: true,
  });
}
