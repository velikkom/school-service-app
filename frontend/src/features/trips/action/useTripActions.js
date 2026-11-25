"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { tripsService } from "@/features/trips/service/tripsService";
import { trackingService } from "@/features/tracking/service/trackingService";
import { attendanceCorrectionsService } from "@/features/attendance-corrections/service/attendanceCorrectionsService";
import { toastError, toastSuccess } from "@/components/feedback/feedback";
import { errorMessage } from "@/lib/http/apiError";
import { externalKeys, tripKeys } from "@/features/trips/hooks/keys";

/** Sefer + bağlı listeleri yeniler (roster, olaylar, sefer, listeler). */
function useInvalidateTrip() {
  const qc = useQueryClient();
  return (tripId) =>
    Promise.all([
      qc.invalidateQueries({ queryKey: tripKeys.detail(tripId) }),
      qc.invalidateQueries({ queryKey: tripKeys.roster(tripId) }),
      qc.invalidateQueries({ queryKey: tripKeys.incidents(tripId) }),
      qc.invalidateQueries({ queryKey: tripKeys.notices(tripId) }),
      qc.invalidateQueries({ queryKey: tripKeys.lists() }),
      qc.invalidateQueries({ queryKey: ["trips", "mine"] }),
      qc.invalidateQueries({ queryKey: externalKeys.dailyService() }),
    ]);
}

/** Hata: toast + çağırana yeniden fırlat (dialog içi mesaj için). */
const failWith = (fallback) => (err) => toastError(errorMessage(err, fallback));

export function useCreateTrip() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body) => tripsService.create(body),
    onSuccess: () => {
      toastSuccess("Sefer oluşturuldu");
      qc.invalidateQueries({ queryKey: tripKeys.lists() });
    },
  });
}

export function useUpdateTrip(tripId) {
  const invalidate = useInvalidateTrip();
  return useMutation({
    mutationFn: (body) => tripsService.update(tripId, body),
    onSuccess: () => {
      toastSuccess("Sefer güncellendi");
      return invalidate(tripId);
    },
  });
}

export function useTripTransition(tripId) {
  const invalidate = useInvalidateTrip();
  return useMutation({
    mutationFn: (kind) => (kind === "start" ? tripsService.start(tripId) : tripsService.complete(tripId)),
    onSuccess: (_d, kind) => {
      toastSuccess(kind === "start" ? "Sefer başlatıldı" : "Sefer tamamlandı");
      return invalidate(tripId);
    },
    onError: failWith("Sefer durumu değiştirilemedi."),
  });
}

export function useCancelTrip(tripId) {
  const invalidate = useInvalidateTrip();
  return useMutation({
    mutationFn: (reason) => tripsService.cancel(tripId, reason),
    onSuccess: () => {
      toastSuccess("Sefer iptal edildi", "Yolcu velileri ve görevliler bilgilendirildi.");
      return invalidate(tripId);
    },
    onError: failWith("Sefer iptal edilemedi."),
  });
}

/** Yoklama: { type: BOARDING|DROPOFF|ABSENCE, studentId, reason? } */
export function useRecordAttendance(tripId) {
  const invalidate = useInvalidateTrip();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ type, studentId, reason }) => {
      if (type === "BOARDING") return tripsService.recordBoarding(tripId, studentId);
      if (type === "DROPOFF") return tripsService.recordDropoff(tripId, studentId);
      return tripsService.recordAbsence(tripId, studentId, reason);
    },
    onSuccess: (_d, { type, studentName }) => {
      const label = { BOARDING: "Biniş kaydedildi", DROPOFF: "İniş kaydedildi", ABSENCE: "Devamsızlık kaydedildi" }[type];
      toastSuccess(label, studentName);
      qc.invalidateQueries({ queryKey: externalKeys.attendance() });
      return invalidate(tripId);
    },
    onError: failWith("Yoklama kaydedilemedi."),
  });
}

export function useCreateIncident(tripId) {
  const invalidate = useInvalidateTrip();
  return useMutation({
    mutationFn: (body) => tripsService.createIncident(tripId, body),
    onSuccess: (_d, body) => {
      toastSuccess(body.type === "CANCELLATION_REQUEST" ? "İptal talebi yöneticiye gönderildi" : "Kayıt eklendi");
      return invalidate(tripId);
    },
  });
}

export function useResolveIncident(tripId) {
  const invalidate = useInvalidateTrip();
  return useMutation({
    mutationFn: ({ incidentId, approve, note }) => tripsService.resolveIncident(tripId, incidentId, { approve, note }),
    onSuccess: (_d, { approve }) => {
      toastSuccess(approve ? "İptal talebi onaylandı, sefer iptal edildi" : "İptal talebi reddedildi");
      return invalidate(tripId);
    },
    onError: failWith("Karar kaydedilemedi."),
  });
}

/** Personel düzeltme talebi: { studentId, requestedStatus, reason } */
export function useRequestCorrection(tripId) {
  const invalidate = useInvalidateTrip();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body) => attendanceCorrectionsService.create({ tripId: Number(tripId), ...body }),
    onSuccess: () => {
      toastSuccess("Düzeltme talebi gönderildi", "Kayıt, yönetici onaylayana kadar değişmez.");
      qc.invalidateQueries({ queryKey: externalKeys.corrections() });
      return invalidate(tripId);
    },
  });
}

/** DRIVER konum gönderimi. Hata çağırana bırakılır (paylaşım paneli satır içi gösterir). */
export function useSendLocation(tripId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body) => trackingService.send(tripId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: externalKeys.latestLocation(tripId) }),
  });
}
