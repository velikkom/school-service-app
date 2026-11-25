"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { dailyServiceService } from "@/features/daily-service/service/dailyServiceService";
import { toastError, toastSuccess } from "@/components/feedback/feedback";
import { errorMessage } from "@/lib/http/apiError";
import { dailyServiceKeys } from "@/features/daily-service/hooks/keys";

/** "Servise binmeyecek" gönder / geri al. Sonrasında öğrencinin günlük servisi yenilenir. */
export function useAbsenceNoticeActions(studentId) {
  const qc = useQueryClient();
  const refresh = () => qc.invalidateQueries({ queryKey: dailyServiceKeys.byStudent(studentId) });

  const submit = useMutation({
    mutationFn: ({ tripId, note }) => dailyServiceService.submitAbsenceNotice(tripId, studentId, note?.trim() || undefined),
    onSuccess: () => {
      toastSuccess("Bildirim görevliye iletildi", "Şoför ve hostesin yolcu listesinde görünür.");
      refresh();
    },
    onError: (err) => {
      toastError("Bildirim gönderilemedi", errorMessage(err));
      if (err?.status === 409 || err?.status === 404) refresh();
    },
  });

  const withdraw = useMutation({
    mutationFn: ({ tripId, noticeId }) => dailyServiceService.withdrawAbsenceNotice(tripId, noticeId),
    onSuccess: () => {
      toastSuccess("Bildirim geri alındı", "Görevli öğrenciyi durakta bekler.");
      refresh();
    },
    onError: (err) => {
      toastError("Bildirim geri alınamadı", errorMessage(err));
      if (err?.status === 409 || err?.status === 404) refresh();
    },
  });

  return { submit, withdraw };
}
