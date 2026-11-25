"use client";

import { useQuery } from "@tanstack/react-query";
import { studentsService } from "@/features/students/service/studentsService";
import { contractsService } from "@/features/contracts/service/contractsService";
import { toList } from "@/lib/http/page";
import { enrollmentKeys } from "./keys";
import { deriveEnrollmentState } from "./enrollmentState";

/** PARENT öz-kayıt durumu (WIZARD / PENDING / OPEN). Onay beklerken 15 sn'de bir yenilenir. */
export function useEnrollmentState(userId, { enabled = true } = {}) {
  return useQuery({
    queryKey: enrollmentKeys.state(userId),
    enabled,
    queryFn: async () => {
      const students = toList(await studentsService.list({ size: 100 }));
      const lists = await Promise.all(students.map((s) => contractsService.list({ studentId: s.id, size: 50 }).then(toList)));
      const byStudent = Object.fromEntries(students.map((s, i) => [s.id, lists[i]]));
      return deriveEnrollmentState(students, byStudent);
    },
    refetchInterval: (q) => (q.state.data?.state === "PENDING" ? 15_000 : false),
    staleTime: 30_000,
  });
}
