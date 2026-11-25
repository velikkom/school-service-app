"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { stopChangeRequestsService } from "@/features/stop-change-requests/service/stopChangeRequestsService";
import { contractsService } from "@/features/contracts/service/contractsService";
import { toastError, toastSuccess } from "@/components/feedback/feedback";
import { errorMessage, isConflict } from "@/lib/http/apiError";
import { pendingCountKeys } from "../hooks/usePendingCounts";
import { requestKeys } from "../hooks/keys";

function failToast(err) {
  if (err?.status === 400 && err?.fieldErrors?.length) return; // alan hataları formda gösterilir
  toastError(isConflict(err) ? "İşlem mevcut durumla çakışıyor" : "İşlem tamamlanamadı", errorMessage(err));
}

/** Durak değişikliği onay/ret. Sonrasında liste + bekleyen sayaçlar (+ atamalar) yenilenir. */
export function useStopChangeDecision() {
  const qc = useQueryClient();
  const refresh = () => {
    qc.invalidateQueries({ queryKey: requestKeys.stopChangesAll() });
    qc.invalidateQueries({ queryKey: pendingCountKeys.all() });
  };
  const approve = useMutation({
    mutationFn: ({ id, note }) => stopChangeRequestsService.approve(id, note),
    onSuccess: () => {
      toastSuccess("Durak değişikliği onaylandı", "Yeni atama yürürlük tarihinden itibaren geçerli.");
      qc.invalidateQueries({ queryKey: ["assignments"] });
    },
    onError: failToast,
    onSettled: refresh,
  });
  const reject = useMutation({
    mutationFn: ({ id, reason }) => stopChangeRequestsService.reject(id, reason),
    onSuccess: () => toastSuccess("Talep reddedildi", "Veli gerekçeyi bildirim olarak görür."),
    onError: failToast,
    onSettled: refresh,
  });
  return { approve, reject };
}

/** Öğrenci başvurusu (PENDING_APPROVAL sözleşme) onay/ret. */
export function useEnrollmentDecision() {
  const qc = useQueryClient();
  const refresh = () => {
    qc.invalidateQueries({ queryKey: requestKeys.contractsAll() });
    qc.invalidateQueries({ queryKey: pendingCountKeys.all() });
    qc.invalidateQueries({ queryKey: ["students"] });
  };
  const approve = useMutation({
    mutationFn: ({ id, body }) => contractsService.approve(id, body),
    onSuccess: () => toastSuccess("Başvuru onaylandı", "Sözleşme başlatıldı. Güzergâh atamasını ayrıca yapın."),
    onError: failToast,
    onSettled: refresh,
  });
  const reject = useMutation({
    mutationFn: ({ id, reason }) => contractsService.reject(id, reason),
    onSuccess: () => toastSuccess("Başvuru reddedildi", "Veli sonucu bildirimden görür."),
    onError: failToast,
    onSettled: refresh,
  });
  return { approve, reject };
}
