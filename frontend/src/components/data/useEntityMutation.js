"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { confirmAction, toastError, toastSuccess } from "@/components/feedback/feedback";
import { errorMessage } from "@/lib/http/apiError";

/**
 * Mutasyon + başarıda verilen sorgu anahtarlarının yenilenmesi. `invalidate` anahtar dizilerinin
 * dizisidir (önek eşleşmesi), ör. [["students"], ["lookup","students"]].
 */
export function useEntityMutation(mutationFn, invalidate = []) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => Promise.all(invalidate.map((queryKey) => qc.invalidateQueries({ queryKey }))),
  });
}

/**
 * Onaylı işlem: onay dialogu → işlem → toast. Onaylanmazsa false döner.
 * 409 vb. hatada sunucunun Türkçe mesajı toast'ta gösterilir.
 */
export async function runConfirmed({ title, message, acceptLabel, tone = "danger", success, failure = "İşlem tamamlanamadı." }, fn) {
  const ok = await confirmAction({ title, message, acceptLabel, tone });
  if (!ok) return false;
  try {
    await fn();
    if (success) toastSuccess(success);
    return true;
  } catch (err) {
    toastError(failure, errorMessage(err, failure));
    return false;
  }
}

/**
 * Düzenlenecek kaydı getirir: listede varsa onu kullanır (ek istek yok), yoksa tekil uçtan çeker
 * (ör. `?duzenle=12` doğrudan açıldığında kayıt mevcut sayfada değilse).
 */
export function useRecord(queryKey, fetcher, id, fromList) {
  const query = useQuery({
    queryKey: [...queryKey, String(id)],
    queryFn: () => fetcher(id),
    enabled: Boolean(id) && !fromList,
  });
  return { record: fromList ?? query.data ?? null, query, isLoading: Boolean(id) && !fromList && query.isPending, error: fromList ? null : query.error };
}
