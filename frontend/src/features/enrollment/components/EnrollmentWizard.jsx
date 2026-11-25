"use client";

import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { Checkbox } from "primereact/checkbox";
import { SelectButton } from "primereact/selectbutton";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import ScreenState from "@/components/feedback/ScreenState";
import { DefinitionList } from "@/components/ui/Card";
import { GENDER_LABELS, mapOptions } from "@/lib/formatting/labels";
import { formatDate, todayISO } from "@/lib/formatting/date";
import { toList } from "@/lib/http/page";
import { errorMessage } from "@/lib/http/apiError";
import { schoolsService } from "@/features/schools/service/schoolsService";
import { RELATION_TYPES, WIZARD_STEPS, emptyEnrollment, enrollmentSchema, stepOfField } from "@/features/enrollment/schemas/enrollmentSchema";
import { useDistricts, useNeighborhoods, useProvinces } from "@/features/enrollment/hooks/useAddressLookup";
import { useSubmitEnrollment } from "@/features/enrollment/action/useSubmitEnrollment";
import { enrollmentKeys } from "@/features/enrollment/hooks/keys";
import StepBar from "./StepBar";
import styles from "../style/enrollment.module.scss";

const nameOptions = (list) => (list ?? []).map((x) => ({ value: x.name, label: x.name, id: x.id }));

function SchoolStep({ control, errors }) {
  const schools = useQuery({
    queryKey: enrollmentKeys.schools(),
    queryFn: async () => toList(await schoolsService.list({ isActive: true, size: 100, sort: "name,asc" })),
    staleTime: 5 * 60_000,
  });
  if (schools.isPending) return <ScreenState kind="loading" rows={2} bare />;
  if (schools.isError) return <ScreenState kind="error" compact bare error={schools.error} onRetry={schools.refetch} title="Okul listesi yüklenemedi" />;
  if (!schools.data.length)
    return <Notice tone="warn" title="Kayıt alınan okul yok">Şu anda başvuruya açık okul bulunmuyor. Lütfen kurumla iletişime geçin.</Notice>;
  return (
    <Controller
      name="schoolId"
      control={control}
      render={({ field }) => (
        <Field label="Okul" required error={errors.schoolId?.message} help="Yalnızca kayıt alınan (aktif) okullar listelenir." htmlFor="enr-school">
          <Dropdown
            id="enr-school-dd"
            inputId="enr-school"
            value={field.value === "" ? null : Number(field.value)}
            options={schools.data.map((s) => ({ value: s.id, label: s.name }))}
            optionLabel="label"
            optionValue="value"
            placeholder="Okul seçin"
            filter={schools.data.length > 8}
            onChange={(e) => field.onChange(e.value ?? "")}
            onBlur={field.onBlur}
          />
        </Field>
      )}
    />
  );
}

function StudentStep({ control, register, errors }) {
  return (
    <FormGrid>
      <Field label="Öğrencinin adı soyadı" required full error={errors.student?.fullName?.message} htmlFor="enr-name">
        <InputText id="enr-name" autoComplete="off" {...register("student.fullName")} />
      </Field>
      <Field label="Doğum tarihi" required error={errors.student?.birthDate?.message} htmlFor="enr-birth">
        <InputText id="enr-birth" type="date" max={todayISO()} {...register("student.birthDate")} />
      </Field>
      <Field label="T.C. kimlik no" required error={errors.student?.tcNo?.message} help="11 hane. Başka öğrencide kayıtlıysa başvuru kabul edilmez." htmlFor="enr-tc">
        <InputText id="enr-tc" inputMode="numeric" maxLength={11} autoComplete="off" {...register("student.tcNo")} />
      </Field>
      <Field label="Sınıf" required error={errors.student?.grade?.message} help="Örn. 3-B" htmlFor="enr-grade">
        <InputText id="enr-grade" {...register("student.grade")} />
      </Field>
      <Controller
        name="student.gender"
        control={control}
        render={({ field }) => (
          <Field label="Cinsiyet" required error={errors.student?.gender?.message} htmlFor="enr-gender">
            <SelectButton
              id="enr-gender"
              value={field.value || null}
              options={mapOptions(GENDER_LABELS)}
              optionLabel="label"
              optionValue="value"
              onChange={(e) => field.onChange(e.value ?? "")}
            />
          </Field>
        )}
      />
      <Controller
        name="relationType"
        control={control}
        render={({ field }) => (
          <Field label="Yakınlığınız" required error={errors.relationType?.message} htmlFor="enr-rel">
            <Dropdown
              id="enr-rel-dd"
              inputId="enr-rel"
              value={field.value || null}
              options={RELATION_TYPES.map((r) => ({ value: r, label: r }))}
              optionLabel="label"
              optionValue="value"
              placeholder="Seçin"
              onChange={(e) => field.onChange(e.value ?? "")}
            />
          </Field>
        )}
      />
      <Field label="Sağlık notu (isteğe bağlı)" full error={errors.student?.healthNote?.message} help="Alerji, ilaç vb. görevlinin bilmesi gereken bilgiler." htmlFor="enr-health">
        <InputTextarea id="enr-health" rows={3} maxLength={500} autoResize={false} {...register("student.healthNote")} />
      </Field>
    </FormGrid>
  );
}

/** İl → ilçe → mahalle kademeli seçim; adres servisi hata verirse ilgili alan serbest metne düşer. */
function AddressStep({ control, register, errors, setValue, ids, setIds }) {
  const provinces = useProvinces();
  const districts = useDistricts(ids.provinceId);
  const neighborhoods = useNeighborhoods(ids.districtId);

  const cityFree = provinces.isError;
  const districtFree = cityFree || (Boolean(ids.provinceId) && districts.isError);
  const hoodFree =
    districtFree || (Boolean(ids.districtId) && (neighborhoods.isError || (neighborhoods.isSuccess && neighborhoods.data.length === 0)));

  const pick = (name, list, value, onChange) => {
    const item = list.find((x) => x.value === value);
    onChange(value ?? "");
    if (name === "city") {
      setIds({ provinceId: item?.id ?? null, districtId: null });
      setValue("address.district", "");
      setValue("address.neighborhood", "");
    }
    if (name === "district") {
      setIds((p) => ({ ...p, districtId: item?.id ?? null }));
      setValue("address.neighborhood", "");
    }
  };

  const select = (name, label, query, list, disabled, placeholder) => (
    <Controller
      name={`address.${name}`}
      control={control}
      render={({ field }) => (
        <Field label={label} required error={errors.address?.[name]?.message} htmlFor={`enr-${name}`}>
          <Dropdown
            id={`enr-${name}-dd`}
            inputId={`enr-${name}`}
            value={field.value || null}
            options={list}
            optionLabel="label"
            optionValue="value"
            placeholder={query?.isFetching && !query.data ? "Yükleniyor…" : placeholder}
            filter
            filterLocale="tr"
            emptyMessage="Kayıt yok"
            emptyFilterMessage="Eşleşen kayıt yok"
            disabled={disabled}
            onChange={(e) => pick(name, list, e.value, field.onChange)}
          />
        </Field>
      )}
    />
  );

  const free = (name, label) => (
    <Field label={label} required error={errors.address?.[name]?.message} htmlFor={`enr-${name}`}>
      <InputText id={`enr-${name}`} {...register(`address.${name}`)} />
    </Field>
  );

  return (
    <div className={styles.stack}>
      {(cityFree || districtFree) && (
        <Notice tone="muted">Adres listesi şu anda alınamadı; bilgileri elle yazabilirsiniz.</Notice>
      )}
      <FormGrid>
        {cityFree ? free("city", "İl") : select("city", "İl", provinces, nameOptions(provinces.data), provinces.isPending, "İl seçin")}
        {districtFree
          ? free("district", "İlçe")
          : select("district", "İlçe", districts, nameOptions(districts.data), !ids.provinceId, ids.provinceId ? "İlçe seçin" : "Önce il seçin")}
        {hoodFree
          ? free("neighborhood", "Mahalle")
          : select("neighborhood", "Mahalle", neighborhoods, nameOptions(neighborhoods.data), !ids.districtId, ids.districtId ? "Mahalle seçin" : "Önce ilçe seçin")}
        <Field label="Telefonunuz" required error={errors.parentPhone?.message} help="Görevli ve kurum size bu numaradan ulaşır." htmlFor="enr-phone">
          <InputText id="enr-phone" type="tel" inputMode="tel" autoComplete="tel" maxLength={20} {...register("parentPhone")} />
        </Field>
        <Field label="Açık adres" required full error={errors.address?.addressLine?.message} help="Sokak, bina ve daire bilgisi" htmlFor="enr-line">
          <InputTextarea id="enr-line" rows={2} maxLength={500} autoResize={false} {...register("address.addressLine")} />
        </Field>
      </FormGrid>
    </div>
  );
}

function ContractStep({ control, errors, values, schoolName, mode }) {
  return (
    <div className={styles.stack}>
      <DefinitionList
        items={[
          ["Öğrenci", `${values.student.fullName || "—"} · ${values.student.birthDate ? formatDate(values.student.birthDate) : "—"}`],
          ["Okul", `${schoolName ?? "—"} · ${values.student.grade || "—"}`],
          ["Adres", [values.address.addressLine, values.address.neighborhood, values.address.district, values.address.city].filter(Boolean).join(", ") || "—"],
          ["Veli", `${values.relationType || "—"} · ${values.parentPhone || "—"}`],
        ]}
      />
      <div className={styles.contractBox}>
        <b>Servis hizmet sözleşmesi</b>
        <p>
          Aylık servis ücreti kurumun belirlediği tutardır; yönetici başvuruyu onaylarken kesinleşir ve sözleşme onaydan sonra aktif olur.
          Ücret ve ödemeleri onaydan sonra “Ödemeler” ekranında görebilirsiniz.
        </p>
      </div>
      <Controller
        name="contractAccepted"
        control={control}
        render={({ field }) => (
          <div className={styles.checkRow}>
            <Checkbox inputId="enr-accept" checked={field.value === true} onChange={(e) => field.onChange(e.checked === true)} aria-invalid={Boolean(errors.contractAccepted)} />
            <label htmlFor="enr-accept">Sözleşme koşullarını okudum ve kabul ediyorum.</label>
          </div>
        )}
      />
      {errors.contractAccepted && (
        <span className={styles.error} role="alert">
          {errors.contractAccepted.message}
        </span>
      )}
      <Notice tone="info">
        {mode === "first"
          ? "İlk başvurunuz onaylanana kadar uygulamanın diğer bölümleri kapalı kalır."
          : "Bu ek çocuk başvurusudur. Mevcut çocuklarınızın servis bilgileri etkilenmez."}
      </Notice>
    </div>
  );
}

/**
 * 4 adımlı öğrenci başvuru sihirbazı (okul → öğrenci → adres → sözleşme). Hem ilk öz-kayıtta
 * (EnrollmentGate, mode="first") hem ikinci çocukta (AddStudentScreen) kullanılır.
 */
export default function EnrollmentWizard({ mode = "additional", onDone }) {
  const [step, setStep] = useState(0);
  const [formError, setFormError] = useState(null);
  const [ids, setIds] = useState({ provinceId: null, districtId: null });
  const mutation = useSubmitEnrollment();
  const form = useForm({ resolver: zodResolver(enrollmentSchema), defaultValues: emptyEnrollment(), mode: "onTouched" });
  const {
    control,
    register,
    handleSubmit,
    trigger,
    setError,
    setValue,
    getValues,
    formState: { errors },
  } = form;
  const qc = useQueryClient();
  const values = getValues();
  const schoolName = (qc.getQueryData(enrollmentKeys.schools()) ?? []).find((s) => s.id === Number(values.schoolId))?.name;

  const next = async () => {
    const ok = await trigger(WIZARD_STEPS[step].fields, { shouldFocus: true });
    if (ok) setStep((s) => Math.min(s + 1, WIZARD_STEPS.length - 1));
  };

  const submit = handleSubmit(
    (v) => {
      setFormError(null);
      mutation.mutate(v, {
        onSuccess: (res) => onDone?.(res),
        onError: (err) => {
          const fe = err?.fieldErrors ?? [];
          if (fe.length) {
            fe.forEach((f) => f?.field && setError(f.field, { type: "server", message: f.message }));
            const steps = fe.map((f) => stepOfField(f.field)).filter((x) => x != null);
            if (steps.length) setStep(Math.min(...steps));
            setFormError("Bazı bilgiler geçersiz. İşaretli alanları düzeltin.");
          } else {
            setFormError(errorMessage(err, "Başvuru gönderilemedi."));
          }
        },
      });
    },
    (errs) => {
      const firstBad = WIZARD_STEPS.findIndex((s) =>
        s.fields.some((f) => f.split(".").reduce((o, k) => (o ? o[k] : undefined), errs))
      );
      if (firstBad >= 0 && firstBad !== step) setStep(firstBad);
    }
  );

  const last = step === WIZARD_STEPS.length - 1;

  return (
    <form
      className={styles.wizard}
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        if (last) submit(e);
        else next();
      }}
    >
      <div className={styles.wizardBody}>
        <StepBar steps={WIZARD_STEPS.map((s) => s.label)} current={step} />
        <h2 className={styles.stepTitle}>
          {["Okul seçimi", "Öğrenci bilgileri", "Adres ve iletişim", "Sözleşme onayı"][step]}
        </h2>
        {formError && (
          <Notice tone="danger" title="Başvuru gönderilemedi">
            {formError}
          </Notice>
        )}
        {step === 0 && <SchoolStep control={control} errors={errors} />}
        {step === 1 && <StudentStep control={control} register={register} errors={errors} />}
        {step === 2 && <AddressStep control={control} register={register} errors={errors} setValue={setValue} ids={ids} setIds={setIds} />}
        {step === 3 && <ContractStep control={control} errors={errors} values={values} schoolName={schoolName} mode={mode} />}
      </div>
      <div className={styles.wizardFoot}>
        {step > 0 ? (
          <Button type="button" label="Geri" icon="pi pi-arrow-left" className="p-button-secondary" onClick={() => setStep((s) => s - 1)} disabled={mutation.isPending} />
        ) : (
          <span />
        )}
        {last ? (
          <Button type="submit" label="Başvuruyu gönder" icon="pi pi-send" loading={mutation.isPending} disabled={mutation.isPending} />
        ) : (
          <Button type="submit" label="Devam" icon="pi pi-arrow-right" iconPos="right" />
        )}
      </div>
    </form>
  );
}
