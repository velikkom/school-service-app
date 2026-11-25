"use client";

import { useEffect, useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import AppDialog from "@/components/feedback/AppDialog";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { optionsOf } from "@/lib/formatting/labels";
import { addDaysISO, formatTime, todayISO } from "@/lib/formatting/date";
import { errorMessage } from "@/lib/http/apiError";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { makeStopChangeSchema } from "@/features/stop-change-requests/schemas/stopChangeSchema";
import { useStopOptions } from "@/features/stop-change-requests/hooks/useStopChangeRequests";
import { useCreateStopChangeRequest } from "@/features/stop-change-requests/action/useCreateStopChangeRequest";

const empty = (studentId) => ({
  studentId: studentId ?? null,
  direction: null,
  requestedStopId: null,
  effectiveDate: addDaysISO(todayISO(), 1),
  reason: "",
});

/** "Durak değişikliği iste" dialogu (veli). Durak listesi seçilen öğrenci + yön için API'den gelir. */
export default function StopChangeRequestDialog({ visible, onHide, students, defaultStudentId }) {
  const today = todayISO();
  const schema = useMemo(() => makeStopChangeSchema(today), [today]);
  const mutation = useCreateStopChangeRequest();
  const [formError, setFormError] = useState(null);
  const {
    control,
    register,
    handleSubmit,
    reset,
    setError,
    setValue,
    formState: { errors },
  } = useForm({ resolver: zodResolver(schema), defaultValues: empty(defaultStudentId) });

  useEffect(() => {
    if (visible) {
      reset(empty(defaultStudentId ?? (students?.length === 1 ? students[0].id : null)));
    }
  }, [visible, reset, defaultStudentId, students]);

  const studentId = useWatch({ control, name: "studentId" });
  const direction = useWatch({ control, name: "direction" });
  const reason = useWatch({ control, name: "reason" }) ?? "";
  const stops = useStopOptions(studentId, direction);

  const stopOptions = (stops.data ?? []).map((o) => ({
    value: o.stopId,
    label: `${o.routeName} · ${o.stopName} (${formatTime(o.plannedTime)})`,
  }));

  const close = () => {
    setFormError(null);
    onHide();
  };

  const onSubmit = handleSubmit((values) => {
    setFormError(null);
    mutation.mutate(values, {
      onSuccess: () => close(),
      onError: (err) => {
        if (!applyServerFieldErrors(err, setError)) setFormError(errorMessage(err, "Talep gönderilemedi."));
      },
    });
  });

  let stopHelp = "Önce öğrenci ve yönü seçin.";
  if (studentId && direction) {
    if (stops.isFetching && !stops.data) stopHelp = "Duraklar yükleniyor…";
    else if (stops.isError) stopHelp = errorMessage(stops.error, "Durak listesi alınamadı.");
    else if (!stopOptions.length) stopHelp = "Bu öğrenci ve yön için seçilebilir durak yok.";
    else stopHelp = "Seçilen öğrenci ve yön için aktif güzergâh durakları.";
  }

  return (
    <AppDialog
      visible={visible}
      onHide={close}
      title="Durak değişikliği iste"
      icon="pi-map-marker"
      tone="info"
      submitLabel="Talebi gönder"
      submitIcon="pi-send"
      onSubmit={onSubmit}
      submitting={mutation.isPending}
      formId="stop-change-form"
      wide
    >
      {formError && (
        <Notice tone="danger" title="Talep gönderilemedi">
          {formError}
        </Notice>
      )}
      <FormGrid>
        <Controller
          name="studentId"
          control={control}
          render={({ field }) => (
            <Field label="Öğrenci" required error={errors.studentId?.message} htmlFor="scr-student">
              <Dropdown
                id="scr-student-dd"
                inputId="scr-student"
                value={field.value}
                options={(students ?? []).map((s) => ({ value: s.id, label: s.fullName }))}
                optionLabel="label"
                optionValue="value"
                placeholder="Öğrenci seçin"
                onChange={(e) => {
                  field.onChange(e.value);
                  setValue("requestedStopId", null);
                }}
              />
            </Field>
          )}
        />
        <Controller
          name="direction"
          control={control}
          render={({ field }) => (
            <Field label="Yön" required error={errors.direction?.message} htmlFor="scr-direction">
              <Dropdown
                id="scr-direction-dd"
                inputId="scr-direction"
                value={field.value}
                options={optionsOf("direction")}
                optionLabel="label"
                optionValue="value"
                placeholder="Yön seçin"
                onChange={(e) => {
                  field.onChange(e.value);
                  setValue("requestedStopId", null);
                }}
              />
            </Field>
          )}
        />
        <Controller
          name="requestedStopId"
          control={control}
          render={({ field }) => (
            <Field label="Yeni durak" required full error={errors.requestedStopId?.message} help={stopHelp} htmlFor="scr-stop">
              <Dropdown
                id="scr-stop-dd"
                inputId="scr-stop"
                value={field.value}
                options={stopOptions}
                optionLabel="label"
                optionValue="value"
                placeholder="Durak seçin"
                filter={stopOptions.length > 8}
                disabled={!studentId || !direction || !stopOptions.length}
                onChange={(e) => field.onChange(e.value)}
              />
            </Field>
          )}
        />
        <Field label="Geçerlilik tarihi" required error={errors.effectiveDate?.message} help="Bugün veya sonrası" htmlFor="scr-date">
          <InputText id="scr-date" type="date" min={today} {...register("effectiveDate")} />
        </Field>
        <Field label="Açıklama (isteğe bağlı)" full error={errors.reason?.message} help={`${reason.length}/500`} htmlFor="scr-reason">
          <InputTextarea id="scr-reason" rows={3} maxLength={500} autoResize={false} {...register("reason")} />
        </Field>
      </FormGrid>
    </AppDialog>
  );
}
