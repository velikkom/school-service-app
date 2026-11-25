"use client";

import { Suspense, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { z } from "zod";
import PageMeta from "@/components/layout/PageMeta";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import { toastSuccess } from "@/components/feedback/feedback";
import Card, { DefinitionList } from "@/components/ui/Card";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import { useDialogParams, useLookup } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { formatDate } from "@/lib/formatting/date";
import { parentsService } from "@/features/parents/service/parentsService";
import { studentsService } from "@/features/students/service/studentsService";
import { parentKeys } from "@/features/parents/hooks/keys";
import { ParentFormDialog, useParentUsers } from "./ParentManagementScreen";

import { RELATION_OPTIONS as RELATIONS, relationLabel } from "@/lib/formatting/labels";

const linkSchema = z.object({
  studentId: z.string().min(1, "Öğrenci seçin."),
  relationType: z.string().min(1, "Yakınlık seçin."),
});

/** /veliler/[id] (ADMIN) — veli bilgileri + bağlı öğrenciler (bağ kur / kaldır). */
export default function ParentDetailScreen({ id }) {
  return (
    <Suspense fallback={<ScreenState kind="loading" />}>
      <ParentDetail id={id} />
    </Suspense>
  );
}

function ParentDetail({ id }) {
  const query = useQuery({ queryKey: [...parentKeys.detail(), String(id)], queryFn: () => parentsService.get(id) });
  const users = useParentUsers();
  const dlg = useDialogParams();
  const parent = query.data;

  return (
    <>
      <PageMeta title={parent?.fullName ?? "Veli"} crumb="Yönetim / Veliler" back="/veliler" />
      <QueryState query={query} forbiddenHome="/veliler">
        {(p) => {
          const u = users.byId.get(String(p.userId));
          return (
            <div className="grid-main">
              <Card
                title={p.fullName}
                actions={<Button label="Düzenle" icon="pi pi-pencil" size="small" severity="secondary" outlined onClick={() => dlg.openEdit(p.id)} />}
              >
                <DefinitionList
                  items={[
                    ["Telefon", p.phone || "—"],
                    ["E-posta", u?.email ?? "—"],
                    ["T.C. kimlik", <span key="tc" className="mono">{p.tcNo || "—"}</span>],
                    ["Hesap", u ? <ActiveBadge key="acc" value={u.isActive} /> : "—"],
                  ]}
                />
              </Card>
              <LinkedStudents parent={p} />
            </div>
          );
        }}
      </QueryState>
      <ParentFormDialog visible={Boolean(dlg.editId)} editId={dlg.editId} fromList={parent && String(parent.id) === dlg.editId ? parent : undefined} onHide={dlg.close} users={users} />
    </>
  );
}

function LinkedStudents({ parent }) {
  const linksQuery = useQuery({ queryKey: parentKeys.links(parent.id), queryFn: () => parentsService.links({ parentId: parent.id }) });
  const students = useLookup(["students"], () => studentsService.list({ size: 100, sort: "fullName,asc" }), { label: (s) => s.fullName });
  const [open, setOpen] = useState(false);
  const revoke = useEntityMutation((linkId) => parentsService.revokeLink(linkId), [["parent-links"]]);
  const nameOf = (sid) => students.byId.get(String(sid))?.fullName ?? `Öğrenci #${sid}`;

  const remove = (l) =>
    runConfirmed(
      {
        title: "Bağı kaldır",
        message: `${parent.fullName} artık ${nameOf(l.studentId)} öğrencisinin verilerine erişemez. Kayıt silinmez, geçmişte görünür.`,
        acceptLabel: "Bağı kaldır",
        success: "Bağ kaldırıldı",
      },
      () => revoke.mutateAsync(l.id)
    );

  return (
    <>
      <ManagementList
        title="Bağlı öğrenciler"
        query={linksQuery}
        paginated={false}
        actions={<Button label="Öğrenci bağla" icon="pi pi-link" onClick={() => setOpen(true)} />}
        emptyIcon="pi-link"
        emptyTitle="Bağlı öğrenci yok"
        emptyMessage="“Öğrenci bağla” ile veli–öğrenci bağı kurun."
        columns={[
          { key: "student", header: "Öğrenci", main: true, body: (l) => <MainCell title={nameOf(l.studentId)} sub={relationLabel(l.relationType)} href={`/ogrenciler/${l.studentId}`} /> },
          {
            key: "status",
            header: "Durum",
            body: (l) =>
              l.isActive ? <ActiveBadge value /> : <StatusBadge tone="muted" icon="pi-ban" label={`Kaldırıldı ${l.revokedAt ? formatDate(l.revokedAt) : ""}`} />,
          },
        ]}
        rowActions={(l) => (l.isActive ? <Button label="Bağı kaldır" size="small" text severity="danger" onClick={() => remove(l)} /> : null)}
      />
      {open && <LinkStudentDialog parent={parent} options={students.options} loading={students.query.isPending} onHide={() => setOpen(false)} />}
    </>
  );
}

function LinkStudentDialog({ parent, options, loading, onHide }) {
  const [serverError, setServerError] = useState(null);
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(linkSchema), defaultValues: { studentId: "", relationType: "" } });
  const create = useEntityMutation((body) => parentsService.createLink(body), [["parent-links"]]);
  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await create.mutateAsync({ parentId: parent.id, studentId: Number(v.studentId), relationType: v.relationType });
      toastSuccess("Bağ kuruldu");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError, "Bağ kurulamadı."));
    }
  });
  return (
    <AppDialog visible onHide={onHide} title="Öğrenci bağla" subtitle={parent.fullName} icon="pi-link" tone="ok" submitLabel="Bağla" onSubmit={onSubmit} submitting={create.isPending} formId="parent-link-form">
      {serverError && <Notice tone="danger" title="Bağ kurulamadı">{serverError}</Notice>}
      <Field label="Öğrenci" error={errors.studentId?.message} required htmlFor="pl-student">
        <Controller
          name="studentId"
          control={control}
          render={({ field }) => (
            <Dropdown inputId="pl-student" value={field.value} options={options} placeholder={loading ? "Yükleniyor…" : "Öğrenci seçin"} filter emptyMessage="Öğrenci yok" onChange={(e) => field.onChange(e.value ?? "")} />
          )}
        />
      </Field>
      <Field label="Yakınlık" error={errors.relationType?.message} required htmlFor="pl-rel">
        <Controller
          name="relationType"
          control={control}
          render={({ field }) => <Dropdown inputId="pl-rel" value={field.value} options={RELATIONS} placeholder="Seçin" onChange={(e) => field.onChange(e.value ?? "")} />}
        />
      </Field>
      <p className="text-xs muted">Aynı veli–öğrenci çifti ikinci kez bağlanamaz.</p>
    </AppDialog>
  );
}
