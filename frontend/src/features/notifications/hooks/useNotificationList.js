"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { notificationsService } from "@/features/notifications/service/notificationsService";
import { pageMeta, toList } from "@/lib/http/page";
import { notificationKeys } from "./keys";

export const NOTIFICATION_PAGE_SIZE = 20;
/** "Okunmamış" görünümü sayfasız: son N bildirim içinden süzülür (okunmamış filtresi API'de yok). */
export const UNREAD_WINDOW = 100;

/** API liste yanıtı IN_APP + EMAIL satırlarını birlikte döndürür (F-13); ekranda yalnız IN_APP. */
export function onlyInApp(list) {
  return (Array.isArray(list) ? list : []).filter((n) => n.channel === "IN_APP");
}

export function useNotificationList({ filter, page }) {
  const unread = filter === "unread";
  const params = unread
    ? { page: 0, size: UNREAD_WINDOW, sort: "createdAt,desc" }
    : { page, size: NOTIFICATION_PAGE_SIZE, sort: "createdAt,desc" };
  return useQuery({
    queryKey: notificationKeys.list({ filter, page: unread ? 0 : page }),
    queryFn: () => notificationsService.list(params),
    select: (resp) => {
      const items = onlyInApp(toList(resp));
      return { items: unread ? items.filter((n) => !n.read) : items, meta: pageMeta(resp) };
    },
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  });
}
