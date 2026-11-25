"use client";

import { useMemo, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import AppDialog from "@/components/feedback/AppDialog";
import { toastSuccess } from "@/components/feedback/feedback";
import Card, { DefinitionList } from "@/components/ui/Card";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import AccountPicker, { TemporaryPasswordNotice } from "@/components/data/AccountPicker";
import { runConfirmed } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { toList } from "@/lib/http/page";
import { studentsService } from "@/features/students/service/studentsService";
import { usersService } from "@/features/users/service/usersService";
import { STUDENT_INVALIDATE } from "@/features/students/hooks/keys";
import { studentAccountSchema } from "@/features/students/schemas/studentSchema";

const linkableKey = ["lookup", "users", "STUDENT", "linkable"];

async function invalidateAfterChange(qc) {
  await Promise.all([...STUDENT_INVALIDATE, ["users"], ["lookup", "users"]].map((queryKey) => qc.invalidateQueries({ queryKey })));
}

/**
 * Öğrenci detayı (ADMIN) — öğrencinin kendi giriş hesabı (STUDENT rolü). Bağ yoksa uygun bir hesabı seçerek
 * ya da yeni hesap oluşturarak bağlar; bağ varsa hesabı gösterir ve onaylı kaldırma sunar. Var olan bağ bu
 * ekrandan değiştirilemez: önce kaldırılır (backend de 409 ile korur).
 */
export default function StudentAccountCard({ student }) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const account = useQuery({
    queryKey: ["users", "detail", String(student.userId)],
    queryFn: () => usersService.get(student.userId),
    enabled: Boolean(student.userId),
  });

  const unlink = () =>
    runConfirmed(
      {
        title: "Hesap bağını kaldır",
        message: `${account.data?.email ?? "Bu hesap"} artık ${student.fullName} öğrencisinin verilerini göremez; açık oturumu yenilenemez. Hesap silinmez.`,
        acceptLabel: "Bağı kaldır",
        success: "Hesap bağı kaldırıldı",
        failure: "Bağ kaldırılamadı.",
      },
      async () => {
        await studentsService.unlinkAccount(student.id);
        await invalidateAfterChange(qc);
      }
    );

  return (
    <Card
      title="Öğrenci hesabı"
      subtitle="Öğrencinin kendi servis, yoklama ve profil bilgisini görebilmesi için STUDENT rolündeki giriş hesabı"
      actions={
        student.userId ? (
          <Button label="Bağı kaldır" icon="pi pi-times" size="small" text severity="danger" onClick={unlink} />
        ) : (
          <Button label="Hesap bağla" icon="pi pi-link" size="small" disabled={!student.isActive} onClick={() => setOpen(true)} />
        )
      }
    >
      {student.userId ? (
        account.isPending ? (
          <span className="muted">Yükleniyor…</span>
        ) : account.data ? (
          <DefinitionList
            items={[
              ["Ad soyad", account.data.fullName],
              ["E-posta", <span key="e" className="mono">{account.data.email}</span>],
              [
                "Durum",
                account.data.isActive ? (
                  <StatusBadge key="s" tone="ok" icon="pi-check" label="Bağlı · aktif" />
                ) : (
                  <StatusBadge key="s" tone="warn" icon="pi-ban" label="Bağlı · hesap pasif (giriş yapamaz)" />
                ),
              ],
            ]}
          />
        ) : (
          <Notice tone="warn">Bağlı hesap (#{student.userId}) okunamadı.</Notice>
        )
      ) : (
        <p className="muted text-sm">{student.isActive ? "Bağlı hesap yok; öğrenci giriş yapamaz." : "Pasif öğrenci kaydına hesap bağlanamaz."}</p>
      )}
      {open && <LinkAccountDialog student={student} onHide={() => setOpen(false)} />}
    </Card>
  );
}

function LinkAccountDialog({ student, onHide }) {
  const qc = useQueryClient();
  const [serverError, setServerError] = useState(null);
  const [created, setCreated] = useState(null);
  const [createdUserId, setCreatedUserId] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const users = useQuery({
    queryKey: linkableKey,
    queryFn: () => usersService.list({ role: "STUDENT", isActive: true, studentLinked: false, size: 100, sort: "fullName,asc" }),
  });
  const options = useMemo(() => toList(users.data).map((u) => ({ value: String(u.id), label: `${u.fullName} · ${u.email}` })), [users.data]);
  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(studentAccountSchema),
    defaultValues: { accountMode: "existing", userId: "", fullName: student.fullName ?? "", email: "", phone: "" },
  });
  const mode = useWatch({ control, name: "accountMode" });

  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    setSubmitting(true);
    let userId = v.accountMode === "existing" ? v.userId : createdUserId;
    let tempPassword = null;
    try {
      if (!userId) {
        const res = await usersService.create({ fullName: v.fullName, email: v.email, phone: v.phone || undefined, role: "STUDENT" });
        userId = String(res.user.id);
        tempPassword = res.temporaryPassword;
        setCreatedUserId(userId);
      }
      await studentsService.linkAccount(student.id, Number(userId));
      await invalidateAfterChange(qc);
      toastSuccess("Öğrenci hesabı bağlandı");
      if (tempPassword) setCreated({ email: v.email, password: tempPassword });
      else onHide();
    } catch (err) {
      if (userId && v.accountMode === "new") {
        // Hesap oluştu ama bağ kurulamadı: tekrar denemede ikinci bir hesap açılmasın.
        setValue("accountMode", "existing");
        setValue("userId", userId);
        await qc.invalidateQueries({ queryKey: linkableKey });
        if (tempPassword) setCreated({ email: v.email, password: tempPassword, partial: true });
      }
      setServerError(formErrorText(err, setError, "Hesap bağlanamadı."));
    } finally {
      setSubmitting(false);
    }
  });

  if (created && !created.partial) {
    return (
      <AppDialog visible onHide={onHide} title="Öğrenci hesabı bağlandı" icon="pi-check" tone="ok" cancelLabel="Kapat">
        <TemporaryPasswordNotice email={created.email} password={created.password} />
      </AppDialog>
    );
  }

  return (
    <AppDialog
      visible
      onHide={onHide}
      title="Öğrenci hesabı bağla"
      subtitle={student.fullName}
      icon="pi-link"
      tone="ok"
      submitLabel="Bağla"
      onSubmit={onSubmit}
      submitting={submitting}
      formId="student-account-form"
      wide
    >
      {serverError && <Notice tone="danger" title="Hesap bağlanamadı">{serverError}</Notice>}
      {created?.partial && <TemporaryPasswordNotice email={created.email} password={created.password} />}
      <AccountPicker
        control={control}
        register={register}
        errors={errors}
        mode={mode}
        userOptions={options}
        loading={users.isPending}
        roleLabel="Öğrenci"
        idPrefix="student-account"
      />
      <p className="text-xs muted">Listede yalnız aktif ve başka bir öğrenciye bağlı olmayan STUDENT hesapları görünür. Bir hesap yalnız bir öğrenciye bağlanır.</p>
    </AppDialog>
  );
}
