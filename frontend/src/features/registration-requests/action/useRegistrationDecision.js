"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { registrationRequestsService } from "@/features/registration-requests/service/registrationRequestsService";
import { toastError, toastSuccess } from "@/components/feedback/feedback";
import { errorMessage, isConflict } from "@/lib/http/apiError";
import { registrationKeys } from "../hooks/keys";

/** Sayaç anahtarı (requests/hooks/usePendingCounts ile aynı) — kabuk menüsü ve Talep Merkezi. */
const PENDING_COUNTS_KEY = ["pending-counts"];

function failToast(err) {
  if (err?.status === 400 && err?.fieldErrors?.length) return; // alan hataları formda gösterilir
  toastError(isConflict(err) ? "İşlem mevcut durumla çakışıyor" : "İşlem tamamlanamadı", errorMessage(err));
}

export function useRegistrationDecision() {
  const qc = useQueryClient();
  const refresh = () => {
    qc.invalidateQueries({ queryKey: registrationKeys.all() });
    qc.invalidateQueries({ queryKey: PENDING_COUNTS_KEY });
  };
  const approve = useMutation({
    mutationFn: ({ id, body }) => registrationRequestsService.approve(id, body),
    onSuccess: (resp) => {
      toastSuccess("Kullanıcı oluşturuldu", resp?.user?.email ? `${resp.user.email} giriş yapabilir.` : undefined);
      qc.invalidateQueries({ queryKey: ["users"] });
    },
    onError: failToast,
    onSettled: refresh,
  });
  const reject = useMutation({
    mutationFn: ({ id, reason }) => registrationRequestsService.reject(id, reason),
    onSuccess: () => toastSuccess("Kayıt talebi reddedildi"),
    onError: failToast,
    onSettled: refresh,
  });
  return { approve, reject };
}
