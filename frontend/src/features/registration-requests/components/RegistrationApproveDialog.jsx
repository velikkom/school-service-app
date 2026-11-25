"use client";

import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { InputText } from "primereact/inputtext";
import { SelectButton } from "primereact/selectbutton";
import AppDialog from "@/components/feedback/AppDialog";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { ROLE_LABELS } from "@/lib/formatting/labels";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { APPROVABLE_ROLES, registrationApproveSchema, toRegistrationApproveBody } from "../schemas/registrationApproveSchema";
import styles from "../style/registration.module.scss";

const ROLE_OPTIONS = APPROVABLE_ROLES.map((value) => ({ value, label: ROLE_LABELS[value] }));

/** "Rol ver, onayla": rol seçimi; DRIVER'da ehliyet no zorunlu, telefon isteğe bağlı. */
export default function RegistrationApproveDialog({ request, submitting, onHide, onApprove }) {
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(registrationApproveSchema),
    defaultValues: { role: "PARENT", phone: "", licenseNo: "" },
  });
  const role = useWatch({ control, name: "role" });

  const submit = handleSubmit(async (values) => {
    try {
      await onApprove(toRegistrationApproveBody(values));
    } catch (err) {
      applyServerFieldErrors(err, setError);
    }
  });

  return (
    <AppDialog
      visible
      onHide={onHide}
      title="Kayıt talebini onayla"
      subtitle={`${request.firstName} ${request.lastName} · ${request.email}`}
      icon="pi-user-plus"
      tone="ok"
      submitLabel="Rol ver, onayla"
      submitIcon="pi-check"
      submitting={submitting}
      onSubmit={submit}
      formId={`reg-approve-${request.id}`}
    >
      <Field label="Rol" required error={errors.role?.message}>
        <Controller
          name="role"
          control={control}
          render={({ field }) => (
            <SelectButton
              className={styles.segment}
              value={field.value}
              options={ROLE_OPTIONS}
              onChange={(e) => e.value && field.onChange(e.value)}
              allowEmpty={false}
              aria-label="Rol"
            />
          )}
        />
      </Field>
      {role === "ADMIN" && (
        <Notice tone="warn" title="Yönetici rolü">
          Bu kullanıcı tüm kayıtlara erişir ve onay/ret kararları verebilir.
        </Notice>
      )}
      {role === "STUDENT" && <Notice tone="muted">Öğrenci kaydı ayrıca Öğrenciler ekranından bu kullanıcıya bağlanmalıdır.</Notice>}
      <Field label="Telefon" error={errors.phone?.message} help={role === "PARENT" ? "Veli için önerilir" : "İsteğe bağlı"}>
        <InputText type="tel" autoComplete="off" maxLength={20} {...register("phone")} />
      </Field>
      {role === "DRIVER" && (
        <Field label="Ehliyet no" required error={errors.licenseNo?.message}>
          <InputText autoComplete="off" maxLength={50} {...register("licenseNo")} />
        </Field>
      )}
      <p className="text-xs muted">Onayla birlikte kullanıcı ve role bağlı profil (veli/şoför/hostes) oluşturulur.</p>
    </AppDialog>
  );
}
