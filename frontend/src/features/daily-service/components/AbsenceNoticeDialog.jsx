"use client";

import { useEffect } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { InputTextarea } from "primereact/inputtextarea";
import AppDialog from "@/components/feedback/AppDialog";
import Notice from "@/components/ui/Notice";
import Field from "@/components/ui/Field";
import { labelOf } from "@/lib/formatting/labels";
import { formatTime } from "@/lib/formatting/date";
import { absenceNoticeSchema } from "@/features/daily-service/schemas/absenceNoticeSchema";

/** "Servise binmeyecek" dialogu — not isteğe bağlı. Resmi yoklama değildir. */
export default function AbsenceNoticeDialog({ visible, onHide, entry, studentName, onSubmit, submitting }) {
  const {
    control,
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({ resolver: zodResolver(absenceNoticeSchema), defaultValues: { note: "" } });

  useEffect(() => {
    if (visible) reset({ note: "" });
  }, [visible, reset]);

  const note = useWatch({ control, name: "note" }) ?? "";
  const subtitle = entry
    ? `${studentName ?? ""} · ${labelOf("direction", entry.direction).short ?? ""} ${formatTime(entry.stopPlannedTime)}`.trim()
    : undefined;

  return (
    <AppDialog
      visible={visible}
      onHide={onHide}
      title="Servise binmeyecek"
      subtitle={subtitle}
      icon="pi-flag"
      tone="notice"
      submitLabel="Bildirimi gönder"
      submitIcon="pi-send"
      onSubmit={handleSubmit((v) => onSubmit(v.note))}
      submitting={submitting}
      formId="absence-notice-form"
    >
      <Notice tone="info">
        Bildirim şoför ve hostesin yolcu listesine düşer. <b>Resmi yoklama değildir</b>: öğrenci durağa gelirse binişi
        kaydedilir. Yoklama girilene veya sefer kapanana kadar geri alabilirsiniz.
      </Notice>
      <Field label="Not (isteğe bağlı)" error={errors.note?.message} help={`${note.length}/500`}>
        <InputTextarea rows={3} maxLength={500} autoResize={false} placeholder="Örn. Doktor randevusu var, ben alacağım" {...register("note")} />
      </Field>
    </AppDialog>
  );
}
