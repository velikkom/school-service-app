"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { stopChangeRequestsService } from "@/features/stop-change-requests/service/stopChangeRequestsService";
import { toastSuccess } from "@/components/feedback/feedback";
import { stopChangeKeys } from "@/features/stop-change-requests/hooks/keys";

/** POST /stop-change-requests (201). Hata gösterimi dialogda (alan hatası / 409 mesajı). */
export function useCreateStopChangeRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (values) =>
      stopChangeRequestsService.create({
        studentId: values.studentId,
        direction: values.direction,
        requestedStopId: values.requestedStopId,
        effectiveDate: values.effectiveDate,
        reason: values.reason?.trim() || undefined,
      }),
    onSuccess: () => {
      toastSuccess("Talebiniz yöneticiye iletildi", "Sonucu bu ekranda ve Bildirimler'de görürsünüz.");
      qc.invalidateQueries({ queryKey: stopChangeKeys.all() });
    },
  });
}
