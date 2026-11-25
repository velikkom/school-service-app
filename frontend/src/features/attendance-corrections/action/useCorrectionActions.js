"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { attendanceCorrectionsService } from "@/features/attendance-corrections/service/attendanceCorrectionsService";
import { toastSuccess } from "@/components/feedback/feedback";
import { correctionKeys } from "@/features/attendance-corrections/hooks/keys";

/** Karar sonrası: talepler + bekleyen sayaçları + ilgili seferin yolcu listesi + yoklama geçmişleri. */
function useInvalidateDecision() {
  const qc = useQueryClient();
  return (correction) =>
    Promise.all([
      qc.invalidateQueries({ queryKey: correctionKeys.all() }),
      qc.invalidateQueries({ queryKey: ["pending-counts"] }),
      qc.invalidateQueries({ queryKey: ["trips", "roster", Number(correction.tripId)] }),
      qc.invalidateQueries({ queryKey: ["attendance"] }),
      qc.invalidateQueries({ queryKey: ["daily-service"] }),
    ]);
}

export function useApproveCorrection() {
  const invalidate = useInvalidateDecision();
  return useMutation({
    mutationFn: ({ correction, newStatus, note }) =>
      attendanceCorrectionsService.approve(correction.id, { newStatus: newStatus || undefined, note: note || undefined }),
    onSuccess: (_d, { correction }) => {
      toastSuccess("Düzeltme uygulandı", "Talep sahibine bildirim gönderildi.");
      return invalidate(correction);
    },
    // 409 (stale/sonuçlanmış) dialog içinde gösterilir; listeyi yine de tazele.
    onError: (_e, { correction }) => invalidate(correction),
  });
}

export function useRejectCorrection() {
  const invalidate = useInvalidateDecision();
  return useMutation({
    mutationFn: ({ correction, reason }) => attendanceCorrectionsService.reject(correction.id, reason),
    onSuccess: (_d, { correction }) => {
      toastSuccess("Talep reddedildi", "Talep sahibine bildirim gönderildi.");
      return invalidate(correction);
    },
    onError: (_e, { correction }) => invalidate(correction),
  });
}
