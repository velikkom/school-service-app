"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { attendanceCorrectionsService } from "@/features/attendance-corrections/service/attendanceCorrectionsService";
import { toastSuccess } from "@/components/feedback/feedback";

/** POST /attendance-corrections (PARENT → REVIEW). Hata işleme dialogda (alan/409 mesajı). */
export function useCreateReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ tripId, studentId, requestedStatus, reason }) =>
      attendanceCorrectionsService.create({ tripId, studentId, requestedStatus: requestedStatus || undefined, reason: reason.trim() }),
    onSuccess: () => {
      toastSuccess("İnceleme talebi gönderildi", "Sonucu Taleplerim ve Bildirimler'de görürsünüz.");
      qc.invalidateQueries({ queryKey: ["attendance-corrections"] });
    },
    onError: (err) => {
      if (err?.status === 409) qc.invalidateQueries({ queryKey: ["attendance-corrections"] });
    },
  });
}
