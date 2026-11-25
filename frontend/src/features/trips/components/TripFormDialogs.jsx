"use client";

import { useId, useMemo, useState } from "react";
import { useController, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import AppDialog from "@/components/feedback/AppDialog";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { labelOf } from "@/lib/formatting/labels";
import { formatDate, formatTime, todayISO } from "@/lib/formatting/date";
import { errorMessage, isConflict } from "@/lib/http/apiError";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { tripCreateSchema, tripEditSchema } from "@/features/trips/schemas/tripSchemas";
import { buildTripPatch } from "@/features/trips/lib/fieldUtils";
import { useTripLookups } from "@/features/trips/hooks/useTripQueries";
import { useCreateTrip, useUpdateTrip } from "@/features/trips/action/useTripActions";

const NO_HOSTESS = "";

/** Seçim alanı (Dropdown) — RHF denetimli, `Field` ile etiketli. */
function SelectField({ control, name, label, options, required, error, placeholder = "Seçin", help, full, filter }) {
  const id = useId();
  const { field } = useController({ name, control });
  return (
    <Field label={label} required={required} error={error} help={help} htmlFor={id} full={full}>
      <Dropdown
        inputId={id}
        value={field.value ?? ""}
        onChange={(e) => field.onChange(e.value ?? "")}
        onBlur={field.onBlur}
        options={options}
        placeholder={placeholder}
        filter={filter}
        emptyMessage="Seçenek yok"
        emptyFilterMessage="Sonuç yok"
      />
    </Field>
  );
}

const asStr = (v) => (v == null ? "" : String(v));

function useOptions(lookups, trip) {
  return useMemo(() => {
    const data = lookups ?? { routes: [], vehicles: [], drivers: [], hostesses: [] };
    const routes = data.routes.map((r) => ({
      value: String(r.id),
      label: `${r.name} · ${labelOf("direction", r.direction).short}`,
      direction: r.direction,
    }));
    const vehicles = data.vehicles.map((v) => ({ value: String(v.id), label: `${v.plate} · ${v.capacity} kişi` }));
    const drivers = data.drivers.map((d) => ({ value: String(d.value), label: d.label }));
    const hostesses = data.hostesses.map((h) => ({ value: String(h.value), label: h.label }));
    // Düzenlemede mevcut atama pasif/listede yoksa adıyla göster.
    if (trip) {
      if (trip.vehicleId && !vehicles.some((o) => o.value === String(trip.vehicleId))) {
        vehicles.unshift({ value: String(trip.vehicleId), label: trip.vehiclePlate ?? `Araç #${trip.vehicleId}` });
      }
      if (trip.driverId && !drivers.some((o) => o.value === String(trip.driverId))) {
        drivers.unshift({ value: String(trip.driverId), label: trip.driverFullName ?? `Şoför #${trip.driverId}` });
      }
      if (trip.hostessId && !hostesses.some((o) => o.value === String(trip.hostessId))) {
        hostesses.unshift({ value: String(trip.hostessId), label: trip.hostessFullName ?? `Hostes #${trip.hostessId}` });
      }
    }
    return { routes, vehicles, drivers, hostesses: [{ value: NO_HOSTESS, label: "Hostes yok" }, ...hostesses] };
  }, [lookups, trip]);
}

function ConflictNotice({ error }) {
  if (!error) return null;
  return (
    <Notice tone="danger" title={isConflict(error) ? "Çakışma" : "Kaydedilemedi"}>
      {errorMessage(error, "Sefer kaydedilemedi.")}
    </Notice>
  );
}

/** ADMIN: Yeni sefer (POST /api/trips). Yön güzergâhtan gelir; araç/şoför/hostes çakışması 409. */
export function TripCreateDialog({ visible, onHide, onCreated, defaultDate }) {
  const lookups = useTripLookups({ enabled: visible });
  const options = useOptions(lookups.data, null);
  const create = useCreateTrip();
  const [serverError, setServerError] = useState(null);
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(tripCreateSchema),
    defaultValues: {
      businessDate: defaultDate || todayISO(),
      plannedStartTime: "",
      routeId: "",
      vehicleId: "",
      driverId: "",
      hostessId: NO_HOSTESS,
    },
  });

  const submit = handleSubmit(async (v) => {
    setServerError(null);
    const route = options.routes.find((r) => r.value === v.routeId);
    try {
      const trip = await create.mutateAsync({
        routeId: Number(v.routeId),
        vehicleId: Number(v.vehicleId),
        driverId: Number(v.driverId),
        hostessId: v.hostessId ? Number(v.hostessId) : undefined,
        businessDate: v.businessDate,
        direction: route?.direction,
        plannedStartTime: v.plannedStartTime || undefined,
      });
      onCreated?.(trip);
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) setServerError(err);
    }
  });

  return (
    <AppDialog
      visible={visible}
      onHide={onHide}
      title="Yeni sefer"
      subtitle="Durum her zaman “Planlandı” başlar. Aynı gün ve yönde araç, şoför veya hostes çakışırsa kaydedilmez."
      icon="pi-car"
      tone="ok"
      wide
      submitLabel="Seferi oluştur"
      submitIcon="pi-check"
      onSubmit={submit}
      submitting={create.isPending}
      formId="trip-create-form"
    >
      {lookups.isError && <Notice tone="danger" title="Seçim listeleri yüklenemedi">{errorMessage(lookups.error)}</Notice>}
      <ConflictNotice error={serverError} />
      <FormGrid>
        <Field label="Tarih" required error={errors.businessDate?.message}>
          <InputText type="date" {...register("businessDate")} />
        </Field>
        <Field label="Planlanan çıkış" error={errors.plannedStartTime?.message} help="İsteğe bağlı · Europe/Istanbul">
          <InputText type="time" {...register("plannedStartTime")} />
        </Field>
        <SelectField
          control={control}
          name="routeId"
          label="Güzergâh"
          required
          full
          filter
          options={options.routes}
          error={errors.routeId?.message}
          help="Yön güzergâhtan gelir; yolcu listesi bu güzergâhın atamalarıdır."
        />
        <SelectField control={control} name="vehicleId" label="Araç" required filter options={options.vehicles} error={errors.vehicleId?.message} />
        <SelectField control={control} name="driverId" label="Şoför" required filter options={options.drivers} error={errors.driverId?.message} />
        <SelectField control={control} name="hostessId" label="Hostes" options={options.hostesses} placeholder="Hostes yok" error={errors.hostessId?.message} />
      </FormGrid>
    </AppDialog>
  );
}

/** ADMIN: Planlı seferi düzenle (PATCH). Güzergâh/tarih/yön salt okunur; yalnız PLANNED. */
export function TripEditDialog({ trip, visible, onHide }) {
  const lookups = useTripLookups({ enabled: visible });
  const options = useOptions(lookups.data, trip);
  const update = useUpdateTrip(trip.id);
  const [serverError, setServerError] = useState(null);
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(tripEditSchema),
    defaultValues: {
      plannedStartTime: trip.plannedStartTime ? formatTime(trip.plannedStartTime) : "",
      vehicleId: asStr(trip.vehicleId),
      driverId: asStr(trip.driverId),
      hostessId: trip.hostessId ? String(trip.hostessId) : NO_HOSTESS,
    },
  });

  const submit = handleSubmit(async (v) => {
    setServerError(null);
    const body = buildTripPatch(trip, v);
    if (!Object.keys(body).length) {
      onHide();
      return;
    }
    try {
      await update.mutateAsync(body);
      onHide();
    } catch (err) {
      if (!applyServerFieldErrors(err, setError)) setServerError(err);
    }
  });

  return (
    <AppDialog
      visible={visible}
      onHide={onHide}
      title="Planlı seferi düzenle"
      subtitle="Sadece “Planlandı” durumundaki sefer düzenlenebilir. Güzergâh, tarih ve yön değiştirilemez."
      icon="pi-pencil"
      tone="info"
      wide
      submitLabel="Değişiklikleri kaydet"
      onSubmit={submit}
      submitting={update.isPending}
      formId="trip-edit-form"
    >
      <ConflictNotice error={serverError} />
      <FormGrid>
        <Field label="Güzergâh">
          <InputText value={trip.routeName ?? ""} readOnly disabled />
        </Field>
        <Field label="Tarih / yön">
          <InputText value={`${formatDate(trip.businessDate)} · ${labelOf("direction", trip.direction).short}`} readOnly disabled />
        </Field>
        <Field label="Planlanan çıkış" error={errors.plannedStartTime?.message} help="Değişirse yolcular ve velileri bilgilendirilir">
          <InputText type="time" {...register("plannedStartTime")} />
        </Field>
        <SelectField control={control} name="vehicleId" label="Araç" required filter options={options.vehicles} error={errors.vehicleId?.message} />
        <SelectField control={control} name="driverId" label="Şoför" required filter options={options.drivers} error={errors.driverId?.message} />
        <SelectField control={control} name="hostessId" label="Hostes" options={options.hostesses} placeholder="Hostes yok" error={errors.hostessId?.message} />
      </FormGrid>
      <Notice tone="info">
        Kaydedilince değişen alanlara göre bildirim gider: saat → veliler/öğrenciler, personel → eklenen ve çıkarılan görevli, araç →
        şoför ve hostes.
      </Notice>
    </AppDialog>
  );
}
