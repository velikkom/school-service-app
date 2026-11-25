"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { enrollmentService } from "@/features/enrollment/service/enrollmentService";
import { toastSuccess } from "@/components/feedback/feedback";
import { toEnrollmentPayload } from "@/features/enrollment/schemas/enrollmentSchema";

/** Öz-kayıt / ikinci çocuk başvurusu. Başarıda kapı durumu, öğrenci ve sözleşme listeleri yenilenir. */
export function useSubmitEnrollment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (values) => enrollmentService.submit(toEnrollmentPayload(values)),
    onSuccess: () => {
      toastSuccess("Başvurunuz yönetici onayına gönderildi", "Onaylandığında bildirim alacaksınız.");
      qc.invalidateQueries({ queryKey: ["enrollment"] });
      qc.invalidateQueries({ queryKey: ["students"] });
      qc.invalidateQueries({ queryKey: ["contracts"] });
    },
  });
}
