"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { settingsService } from "@/features/settings/service/settingsService";

export const settingsKeys = { detail: () => ["settings"] };

export function useSettings() {
  return useQuery({ queryKey: settingsKeys.detail(), queryFn: settingsService.get });
}

/** PATCH /api/settings — yanıt önbelleğe yazılır (Öğrenci Başvuruları ücret önerisi de bunu okur). */
export function useUpdateSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (patch) => settingsService.update(patch),
    onSuccess: (data) => qc.setQueryData(settingsKeys.detail(), data),
  });
}
