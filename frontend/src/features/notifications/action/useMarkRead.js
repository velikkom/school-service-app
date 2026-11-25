"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { notificationsService } from "@/features/notifications/service/notificationsService";
import { toastError, toastSuccess } from "@/components/feedback/feedback";
import { errorMessage } from "@/lib/http/apiError";
import { notificationKeys } from "../hooks/keys";

/**
 * Okundu işaretleme. "Tümü" için ayrı uç yok: `markMany` verilen okunmamışları sırayla işaretler.
 * Sonrasında liste ve zil sayacı (`notificationKeys.unread()`) yenilenir.
 */
export function useMarkRead() {
  const qc = useQueryClient();
  const refresh = () => {
    qc.invalidateQueries({ queryKey: notificationKeys.unread() });
    qc.invalidateQueries({ queryKey: notificationKeys.all() });
  };

  const markOne = useMutation({
    mutationFn: (id) => notificationsService.markRead(id),
    onError: (err) => toastError("Bildirim güncellenemedi", errorMessage(err)),
    onSettled: refresh,
  });

  const markMany = useMutation({
    mutationFn: async (ids) => {
      let done = 0;
      for (const id of ids) {
        await notificationsService.markRead(id);
        done += 1;
      }
      return done;
    },
    onSuccess: (n) => toastSuccess(`${n} bildirim okundu işaretlendi`),
    onError: (err) => toastError("Bazı bildirimler işaretlenemedi", errorMessage(err)),
    onSettled: refresh,
  });

  return { markOne, markMany };
}
