"use client";

import { useMemo } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import PageMeta from "@/components/layout/PageMeta";
import { QueryState } from "@/components/feedback/ScreenState";
import { toastError, toastInfo, toastSuccess } from "@/components/feedback/feedback";
import Card from "@/components/ui/Card";
import Field, { FormGrid } from "@/components/ui/Field";
import { errorMessage } from "@/lib/http/apiError";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { useSettings, useUpdateSettings } from "../hooks/useSettings";
import { buildSettingsPatch, makeSettingsSchema, settingsToForm } from "../schemas/settingsSchema";
import styles from "../style/settings.module.scss";

function SettingsForm({ settings }) {
  const update = useUpdateSettings();
  const schema = useMemo(() => makeSettingsSchema(settings), [settings]);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm({ resolver: zodResolver(schema), defaultValues: settingsToForm(settings) });

  const submit = handleSubmit(async (values) => {
    const patch = buildSettingsPatch(values, settings);
    if (Object.keys(patch).length === 0) {
      toastInfo("Değişiklik yok");
      return;
    }
    try {
      const saved = await update.mutateAsync(patch);
      reset(settingsToForm(saved));
      toastSuccess("Ayarlar kaydedildi");
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) toastError("Ayarlar kaydedilemedi", errorMessage(err));
    }
  });

  return (
    <form noValidate onSubmit={submit} className="stack-lg">
      <FormGrid>
        <Field label="Kurum adı" required full error={errors.name?.message}>
          <InputText maxLength={200} {...register("name")} />
        </Field>
        <Field label="Adres" full error={errors.address?.message} help="Boş bırakılırsa silinir">
          <InputText maxLength={255} {...register("address")} />
        </Field>
        <Field label="Telefon" error={errors.phone?.message}>
          <InputText type="tel" maxLength={20} {...register("phone")} />
        </Field>
        <Field
          label="Varsayılan aylık ücret (TL)"
          error={errors.defaultMonthlyFee?.message}
          help="Öğrenci başvurusu onayında önerilir. Tanımlı değilse veli öz-kaydı yapılamaz."
        >
          <InputText inputMode="decimal" autoComplete="off" {...register("defaultMonthlyFee")} />
        </Field>
        <Field label="Belge notu" full error={errors.reportHeaderNote?.message} help="Makbuz ve ekstrede görünür (en fazla 255 karakter)">
          <InputTextarea rows={3} autoResize={false} maxLength={255} {...register("reportHeaderNote")} />
        </Field>
      </FormGrid>
      <div className={styles.actions}>
        <Button type="submit" label="Kaydet" icon="pi pi-check" loading={update.isPending} disabled={update.isPending || !isDirty} />
        <Button type="button" label="Değişiklikleri geri al" className="p-button-secondary" disabled={!isDirty || update.isPending} onClick={() => reset(settingsToForm(settings))} />
      </div>
    </form>
  );
}

/** ADMIN "Kurum Ayarları" (/ayarlar): GET + yarı-kısmi PATCH (yalnız değişen alanlar). */
export default function SettingsScreen() {
  const query = useSettings();
  return (
    <>
      <PageMeta title="Kurum Ayarları" />
      <div className={styles.wrap}>
        <Card title="Kurum bilgileri">
          <QueryState query={query} loadingRows={4}>
            {(data) => <SettingsForm settings={data} />}
          </QueryState>
        </Card>
      </div>
    </>
  );
}
