"use client";

import { Controller } from "react-hook-form";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { SelectButton } from "primereact/selectbutton";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";

const MODES = [
  { value: "existing", label: "Mevcut hesap" },
  { value: "new", label: "Yeni hesap oluştur" },
];

/**
 * Profil formları (veli/şoför/hostes) için kullanıcı hesabı seçimi veya yeni hesap alanları.
 * Form alanları: accountMode, userId, (showName ise) fullName, email, phone.
 * userOptions: [{value,label}] — profili olmayan, ilgili roldeki hesaplar.
 */
export default function AccountPicker({ control, register, errors, mode, userOptions, loading, roleLabel, showName = true, idPrefix = "acc" }) {
  return (
    <>
      <Field label="Kullanıcı hesabı" htmlFor={`${idPrefix}-mode`} full>
        <Controller
          name="accountMode"
          control={control}
          render={({ field }) => (
            <SelectButton
              id={`${idPrefix}-mode`}
              value={field.value}
              options={MODES}
              optionLabel="label"
              optionValue="value"
              allowEmpty={false}
              onChange={(e) => field.onChange(e.value)}
            />
          )}
        />
      </Field>
      {mode === "existing" ? (
        <Field
          label={`${roleLabel} hesabı`}
          error={errors.userId?.message}
          required
          full
          htmlFor={`${idPrefix}-user`}
          help={userOptions.length === 0 && !loading ? `Profili olmayan ${roleLabel.toLocaleLowerCase("tr")} hesabı yok; “Yeni hesap oluştur” seçin.` : undefined}
        >
          <Controller
            name="userId"
            control={control}
            render={({ field }) => (
              <Dropdown
                inputId={`${idPrefix}-user`}
                value={field.value}
                options={userOptions}
                optionLabel="label"
                optionValue="value"
                placeholder={loading ? "Yükleniyor…" : "Hesap seçin"}
                filter
                filterPlaceholder="Ara"
                emptyMessage="Uygun hesap yok"
                emptyFilterMessage="Sonuç yok"
                onChange={(e) => field.onChange(e.value ?? "")}
                invalid={Boolean(errors.userId)}
              />
            )}
          />
        </Field>
      ) : (
        <>
          <Notice tone="info">Hesap oluşturulur; geçici şifre kayıttan sonra bir kez gösterilir.</Notice>
          <FormGrid>
            {showName && (
              <Field label="Ad soyad" error={errors.fullName?.message} required>
                <InputText {...register("fullName")} />
              </Field>
            )}
            <Field label="E-posta" error={errors.email?.message} required>
              <InputText type="email" inputMode="email" autoComplete="off" {...register("email")} />
            </Field>
            <Field label="Telefon" error={errors.phone?.message}>
              <InputText type="tel" inputMode="tel" {...register("phone")} />
            </Field>
          </FormGrid>
        </>
      )}
    </>
  );
}

/** Oluşturulan hesabın geçici şifresini bir kez gösterir. */
export function TemporaryPasswordNotice({ email, password }) {
  if (!password) return null;
  return (
    <Notice tone="warn" title="Geçici şifre (bir kez gösterilir)">
      <span>
        {email}: <b className="mono">{password}</b> — kullanıcıya güvenli bir kanaldan iletin; ilk girişte değiştirmesini isteyin.
      </span>
    </Notice>
  );
}
