"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { attendanceService } from "@/features/attendance/service/attendanceService";
import { attendanceCorrectionsService } from "@/features/attendance-corrections/service/attendanceCorrectionsService";
import { toList } from "@/lib/http/page";
import { attendanceKeys } from "./keys";
import { endOfDayInstant, startOfDayInstant } from "./attendanceModel";

/** Öğrencinin yoklama olayları (from/to: "YYYY-MM-DD", Istanbul günü). */
export function useAttendanceHistory(studentId, from, to) {
  return useQuery({
    queryKey: attendanceKeys.history(studentId, from, to),
    queryFn: () => attendanceService.forStudent(studentId, { from: startOfDayInstant(from), to: endOfDayInstant(to) }),
    enabled: Boolean(studentId && from && to),
    placeholderData: (prev, prevQuery) => (prevQuery?.queryKey?.[1] === Number(studentId) ? prev : undefined),
  });
}

/** Velinin bekleyen inceleme talepleri (aynı sefer+öğrenci için yeni talep açılmaz — 409). */
export function usePendingReviews({ enabled = true } = {}) {
  return useQuery({
    queryKey: attendanceKeys.pendingCorrections(),
    queryFn: async () => toList(await attendanceCorrectionsService.list({ status: "PENDING", size: 100 })),
    enabled,
    placeholderData: keepPreviousData,
  });
}
