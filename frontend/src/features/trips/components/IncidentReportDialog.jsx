"use client";

import { useId, useState } from "react";
import { useController, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { SelectButton } from "primereact/selectbutton";
import { InputNumber } from "primereact/inputnumber";
import { InputTextarea } from "primereact/inputtextarea";
import AppDialog from "@/components/feedback/AppDialog";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { labelOf } from "@/lib/formatting/labels";
import { errorMessage } from "@/lib/http/apiError";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { incidentSchema } from "@/features/trips/schemas/tripSchemas";
import { useCreateIncident } from "@/features/trips/action/useTripActions";

const TYPES = ["DELAY", "BREAKDOWN", "ROAD_CLOSURE"];

/**
 * Olay bildir / kayıt ekle (POST /api/trips/{id}/incidents). Gecikmede 1–600 dk zorunlu, not zorunlu ≤500.
 * İptal talebi bu dialogda değil, ayrı gerekçe dialogunda açılır. Üst bileşen açılışta `key` ile yeniler.
 */
export default function IncidentReportDialog({ tripId, visible, onHide, title = "Olay bildir" }) {
  const typeId = useId();
  const delayId = useId();
  const [serverError, setServerError] = useState(null);
  const mutation = useCreateIncident(tripId);
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(incidentSchema),
    defaultValues: { type: "DELAY", delayMinutes: null, note: "" },
  });
  const { field: typeField } = useController({ name: "type", control });
  const { field: delayField } = useController({ name: "delayMinutes", control });
  const note = useWatch({ control, name: "note" }) ?? "";
  const isDelay = typeField.value === "DELAY";

  const submit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await mutation.mutateAsync({
        type: v.type,
        delayMinutes: v.type === "DELAY" ? v.delayMinutes : undefined,
        note: v.note.trim(),
      });
      onHide();
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) setServerError(errorMessage(err, "Kayıt eklenemedi."));
    }
  });

  return (
    <AppDialog
      visible={visible}
      onHide={onHide}
      title={title}
      subtitle="Yönetici ve (gecikmede) yolcu velileri bilgilendirilir."
      icon="pi-exclamation-triangle"
      tone="warn"
      submitLabel="Gönder"
      submitIcon="pi-send"
      onSubmit={submit}
      submitting={mutation.isPending}
      formId="incident-report-form"
    >
      {serverError && <Notice tone="danger" title="Gönderilemedi">{serverError}</Notice>}
      <Field label="Tür" required error={errors.type?.message} htmlFor={typeId}>
        <SelectButton
          id={typeId}
          value={typeField.value}
          onChange={(e) => e.value && typeField.onChange(e.value)}
          options={TYPES.map((t) => ({ value: t, label: labelOf("incidentType", t).label }))}
          allowEmpty={false}
        />
      </Field>
      {isDelay && (
        <Field label="Gecikme (dakika)" required error={errors.delayMinutes?.message} help="1–600 dk" htmlFor={delayId}>
          <InputNumber
            inputId={delayId}
            value={delayField.value ?? null}
            onValueChange={(e) => delayField.onChange(e.value ?? null)}
            onBlur={delayField.onBlur}
            min={1}
            max={600}
            suffix=" dk"
            useGrouping={false}
          />
        </Field>
      )}
      <Field label="Not" required error={errors.note?.message} help={`${note.length}/500`}>
        <InputTextarea rows={4} maxLength={500} placeholder="Kısaca açıklayın" {...register("note")} />
      </Field>
    </AppDialog>
  );
}
