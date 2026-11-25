"use client";

import { useQuery } from "@tanstack/react-query";
import { notificationsService } from "@/features/notifications/service/notificationsService";
import { toList } from "@/lib/http/page";
import { notificationKeys } from "./keys";

/**
 * Zil sayacı: ayrı "okunmamış sayısı" ucu olmadığından son 100 bildirimdeki okunmamış IN_APP
 * kayıtlar sayılır (API referansı §10). 60 sn'de bir yenilenir.
 */
export function useUnreadCount({ enabled = true } = {}) {
  return useQuery({
    queryKey: notificationKeys.unread(),
    enabled,
    refetchInterval: 60_000,
    queryFn: async () => {
      const list = toList(await notificationsService.list({ size: 100, sort: "createdAt,desc" }));
      return list.filter((n) => n.channel === "IN_APP" && !n.read).length;
    },
  });
}
