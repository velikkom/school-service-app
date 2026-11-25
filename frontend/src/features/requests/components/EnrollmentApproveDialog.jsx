"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { InputText } from "primereact/inputtext";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";
import { DefinitionList } from "@/components/ui/Card";
import Field, { FormGrid } from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { formatDate } from "@/lib/formatting/date";
import { formatMoney } from "@/lib/formatting/money";
import { GENDER_LABELS } from "@/lib/formatting/labels";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { enrollmentApproveSchema, moneyToInput, toApproveBody } from "../schemas/enrollmentApproveSchema";
import { useApplicantDetails, useInstitutionSettings } from "../hooks/useQueues";

function ApplicantSummary({ studentId }) {
  const { student, parent } = useApplicantDetails(studentId);
  if (student.isPending) return <ScreenState kind="loading" rows={2} bare />;
  if (student.isError) return <Notice tone="warn" title="Öğrenci bilgileri alınamadı">Onay yine de yapılabilir.</Notice>;
  const s = student.data;
  const address = [s.addressLine, s.neighborhood, s.district, s.city].filter(Boolean).join(", ");
  const p = parent?.data;
  return (
    <DefinitionList
      items={[
        ["Okul", s.schoolName],
        ["Sınıf", s.grade],
        ["Doğum tarihi", formatDate(s.birthDate)],
        ["Cinsiyet", GENDER_LABELS[s.gender] ?? "—"],
        ["Adres", address || "—"],
        ["Sağlık notu", s.healthNote || "—"],
        ["Veli", parent ? (parent.isPending ? "Yükleniyor…" : p ? `${p.fullName}${p.phone ? ` · ${p.phone}` : ""}` : "—") : "Bağlı veli yok"],
      ]}
    />
  );
}

function ApproveDialogForm({ contract, suggestedFee, submitting, onHide, onApprove, loading }) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(enrollmentApproveSchema),
    defaultValues: {
      monthlyFee: moneyToInput(contract.monthlyFee ?? suggestedFee),
      discountAmount: moneyToInput(contract.discountAmount),
      startDate: contract.startDate ?? "",
    },
  });

  const submit = handleSubmit(async (values) => {
    try {
      await onApprove(toApproveBody(values));
    } catch (err) {
      applyServerFieldErrors(err, setError);
    }
  });

  return (
    <AppDialog
      visible
      onHide={onHide}
      title="Başvuruyu onayla"
      subtitle={contract.studentFullName}
      icon="pi-check"
      tone="ok"
      wide
      formId={`enr-approve-${contract.id}`}
      submitLabel="Onayla, sözleşmeyi başlat"
      submitIcon="pi-check"
      submitting={submitting}
      submitDisabled={loading}
      onSubmit={submit}
    >
      <div className="stack-lg">
        <ApplicantSummary studentId={contract.studentId} />
        {loading ? (
          <ScreenState kind="loading" rows={1} bare />
        ) : (
          <FormGrid>
            <Field
              label="Aylık ücret (TL)"
              required
              error={errors.monthlyFee?.message}
              help={suggestedFee && contract.monthlyFee == null ? `Kurum varsayılanı önerildi: ${formatMoney(suggestedFee)}` : undefined}
            >
              <InputText inputMode="decimal" autoComplete="off" {...register("monthlyFee")} />
            </Field>
            <Field label="İndirim (TL)" error={errors.discountAmount?.message} help="İsteğe bağlı">
              <InputText inputMode="decimal" autoComplete="off" {...register("discountAmount")} />
            </Field>
            <Field label="Başlangıç tarihi" error={errors.startDate?.message} help="Boş bırakılırsa başvurudaki tarih kullanılır">
              <InputText type="date" {...register("startDate")} />
            </Field>
          </FormGrid>
        )}
        <Notice tone="muted">Onay sonrası öğrencinin güzergâh ataması ayrıca yapılmalıdır (Öğrenci Atamaları).</Notice>
      </div>
    </AppDialog>
  );
}

/**
 * "İncele ve onayla": başvuru özeti + ücret formu. monthlyFee zorunlu; kurum varsayılanı
 * (`GET /api/settings` → defaultMonthlyFee) önerilir. Ayarlar gelene kadar form beklemede kalır;
 * ayarlar gelince form varsayılanlarla yeniden kurulur (key).
 */
export default function EnrollmentApproveDialog({ contract, submitting, onHide, onApprove }) {
  const settings = useInstitutionSettings();
  const loading = settings.isPending;
  return (
    <ApproveDialogForm
      key={loading ? "loading" : "ready"}
      contract={contract}
      suggestedFee={settings.data?.defaultMonthlyFee}
      loading={loading}
      submitting={submitting}
      onHide={onHide}
      onApprove={onApprove}
    />
  );
}
