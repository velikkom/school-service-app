"use client";

import { useEffect, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Dropdown } from "primereact/dropdown";
import { InputTextarea } from "primereact/inputtextarea";
import AppDialog from "@/components/feedback/AppDialog";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { labelOf } from "@/lib/formatting/labels";
import { formatDate } from "@/lib/formatting/date";
import { errorMessage } from "@/lib/http/apiError";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { reviewSchema } from "@/features/attendance/schemas/reviewSchema";
import { reviewTargetOptions } from "@/features/attendance/hooks/attendanceModel";
import { useCreateReview } from "@/features/attendance/action/useCreateReview";

/** "Yoklama incelemesi iste" — veli bir sefer kaydının kontrol edilmesini ister (kind=REVIEW). */
export default function ReviewRequestDialog({ visible, onHide, row, student }) {
  const mutation = useCreateReview();
  const [formError, setFormError] = useState(null);
  const {
    control,
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(reviewSchema), defaultValues: { requestedStatus: null, reason: "" } });

  useEffect(() => {
    if (visible) {
      reset({ requestedStatus: null, reason: "" });
    }
  }, [visible, reset]);

  const reason = useWatch({ control, name: "reason" }) ?? "";
  const current = row?.status ?? "NONE";
  const options = reviewTargetOptions(current).map((v) => ({ value: v, label: labelOf("attendance", v).label }));

  const close = () => {
    setFormError(null);
    onHide();
  };

  const onSubmit = handleSubmit((values) => {
    setFormError(null);
    mutation.mutate(
      { tripId: row.tripId, studentId: student.id, requestedStatus: values.requestedStatus, reason: values.reason },
      {
        onSuccess: () => close(),
        onError: (err) => {
          if (!applyServerFieldErrors(err, setError)) setFormError(errorMessage(err, "Talep gönderilemedi."));
        },
      }
    );
  });

  return (
    <AppDialog
      visible={visible}
      onHide={close}
      title="Yoklama incelemesi iste"
      subtitle={row ? `${student?.fullName ?? ""} · ${formatDate(row.firstAt)} · kayıt: ${labelOf("attendance", current).label}` : undefined}
      icon="pi-search"
      tone="warn"
      submitLabel="Gönder"
      submitIcon="pi-send"
      onSubmit={onSubmit}
      submitting={mutation.isPending}
      formId="review-request-form"
    >
      {formError && (
        <Notice tone="danger" title="Talep gönderilemedi">
          {formError}
        </Notice>
      )}
      <Notice tone="muted">Kayıt hemen değişmez; yönetici inceleyip karar verir. Sonucu Taleplerim ve Bildirimler&apos;de görürsünüz.</Notice>
      <Controller
        name="requestedStatus"
        control={control}
        render={({ field }) => (
          <Field label="Olması gerektiğini düşündüğünüz durum (isteğe bağlı)" error={errors.requestedStatus?.message} htmlFor="review-status">
            <Dropdown
              id="review-status-dd"
              inputId="review-status"
              value={field.value}
              onChange={(e) => field.onChange(e.value ?? null)}
              onBlur={field.onBlur}
              options={options}
              optionLabel="label"
              optionValue="value"
              placeholder="Bilmiyorum / sadece kontrol edilsin"
              showClear
            />
          </Field>
        )}
      />
      <Field label="Açıklama" required error={errors.reason?.message} help={`${reason.length}/500`}>
        <InputTextarea rows={4} maxLength={500} autoResize={false} placeholder="Ne olduğunu kısaca açıklayın" {...register("reason")} />
      </Field>
    </AppDialog>
  );
}
