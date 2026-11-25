"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { notificationsService } from "@/features/notifications/service/notificationsService";
import { toastError, toastSuccess } from "@/components/feedback/feedback";
import { errorMessage } from "@/lib/http/apiError";
import { notificationKeys } from "../hooks/keys";

export function useBroadcast() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body) => notificationsService.broadcast(body),
    onSuccess: (resp) => {
      toastSuccess("Duyuru gönderildi", `${resp?.recipientCount ?? 0} alıcıya iletildi.`);
      qc.invalidateQueries({ queryKey: notificationKeys.all() });
    },
    onError: (err) => {
      if (err?.status === 400 && err?.fieldErrors?.length) return;
      toastError("Duyuru gönderilemedi", errorMessage(err));
    },
  });
}
