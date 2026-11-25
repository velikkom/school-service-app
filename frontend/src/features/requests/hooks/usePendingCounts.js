"use client";

import { useQuery } from "@tanstack/react-query";
import { attendanceCorrectionsService } from "@/features/attendance-corrections/service/attendanceCorrectionsService";
import { stopChangeRequestsService } from "@/features/stop-change-requests/service/stopChangeRequestsService";
import { contractsService } from "@/features/contracts/service/contractsService";
import { registrationRequestsService } from "@/features/registration-requests/service/registrationRequestsService";

export const pendingCountKeys = { all: () => ["pending-counts"] };

const total = (p) => p.then((r) => r?.totalElements ?? 0).catch(() => null);

/**
 * ADMIN bekleyen iş sayaçları (menü ve Talep Merkezi). Dört liste ucunun `totalElements` değeri,
 * size=1 ile okunur. 60 sn'de bir yenilenir; karar mutasyonları bu anahtarı geçersiz kılar.
 */
export function usePendingCounts({ enabled = true } = {}) {
  return useQuery({
    queryKey: pendingCountKeys.all(),
    enabled,
    refetchInterval: 60_000,
    queryFn: async () => {
      const [corrections, stopChanges, enrollments, registrations] = await Promise.all([
        total(attendanceCorrectionsService.list({ status: "PENDING", size: 1 })),
        total(stopChangeRequestsService.list({ status: "PENDING", size: 1 })),
        total(contractsService.list({ status: "PENDING_APPROVAL", size: 1 })),
        total(registrationRequestsService.list({ status: "EMAIL_VERIFIED", size: 1 })),
      ]);
      const all = [corrections, stopChanges, enrollments, registrations].reduce((s, n) => s + (n ?? 0), 0);
      return { corrections, stopChanges, enrollments, registrations, all };
    },
  });
}
