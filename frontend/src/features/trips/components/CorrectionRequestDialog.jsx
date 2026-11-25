"use client";

import { useId, useState } from "react";
import { useController, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Dropdown } from "primereact/dropdown";
import { InputTextarea } from "primereact/inputtextarea";
import AppDialog from "@/components/feedback/AppDialog";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { labelOf } from "@/lib/formatting/labels";
import { errorMessage } from "@/lib/http/apiError";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { correctionRequestSchema } from "@/features/trips/schemas/tripSchemas";
import { correctionTargets } from "@/features/trips/lib/fieldUtils";
import { useRequestCorrection } from "@/features/trips/action/useTripActions";

/**
 * Personel yoklama düzeltme talebi (POST /api/attendance-corrections). Hedef durum ve gerekçe zorunlu.
 * Kayıt, yönetici onaylayana kadar değişmez. Üst bileşen `row` değiştiğinde `key` ile yeniden kurar.
 */
export default function CorrectionRequestDialog({ tripId, row, onHide }) {
  const statusId = useId();
  const [serverError, setServerError] = useState(null);
  const mutation = useRequestCorrection(tripId);
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(correctionRequestSchema),
    defaultValues: { requestedStatus: undefined, reason: "" },
  });

  const { field: statusField } = useController({ name: "requestedStatus", control });
  const options = correctionTargets(row?.status).map((s) => ({ value: s, label: labelOf("attendance", s).label }));
  const reason = useWatch({ control, name: "reason" }) ?? "";

  const submit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      await mutation.mutateAsync({ studentId: row.studentId, requestedStatus: values.requestedStatus, reason: values.reason.trim() });
      onHide();
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) setServerError(errorMessage(err, "Talep gönderilemedi."));
    }
  });

  return (
    <AppDialog
      visible={Boolean(row)}
      onHide={onHide}
      title="Yoklama düzeltme talebi"
      subtitle={row ? `${row.studentFullName} · mevcut kayıt: ${labelOf("attendance", row.status).label}` : undefined}
      icon="pi-history"
      tone="warn"
      submitLabel="Talebi gönder"
      submitIcon="pi-send"
      onSubmit={submit}
      submitting={mutation.isPending}
      formId="correction-request-form"
    >
      <Notice tone="info">Talep yöneticiye gider. Kayıt, onaylanana kadar değişmez.</Notice>
      {serverError && <Notice tone="danger" title="Talep gönderilemedi">{serverError}</Notice>}
      <Field label="Olması gereken durum" required error={errors.requestedStatus?.message} htmlFor={statusId}>
        <Dropdown
          inputId={statusId}
          value={statusField.value ?? null}
          onChange={(e) => statusField.onChange(e.value)}
          onBlur={statusField.onBlur}
          options={options}
          placeholder="Seçin"
        />
      </Field>
      <Field label="Gerekçe" required error={errors.reason?.message} help={`${reason.length}/500`}>
        <InputTextarea rows={4} maxLength={500} placeholder="Ne oldu? Örn. öğrenci ikinci durakta bindi" {...register("reason")} />
      </Field>
    </AppDialog>
  );
}
