"use client";

import { Suspense, useMemo, useState } from "react";
import Link from "next/link";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { TabMenu } from "primereact/tabmenu";
import { useAuth } from "@/providers/AuthProvider";
import PageMeta from "@/components/layout/PageMeta";
import RoleRedirect from "@/components/layout/RoleRedirect";
import AppDialog from "@/components/feedback/AppDialog";
import ScreenState, { QueryState } from "@/components/feedback/ScreenState";
import { toastError, toastSuccess } from "@/components/feedback/feedback";
import Card, { DefinitionList, MetricCard } from "@/components/ui/Card";
import Field from "@/components/ui/Field";
import Notice, { Avatar } from "@/components/ui/Notice";
import StatusBadge from "@/components/ui/StatusBadge";
import ManagementList, { ActiveBadge, MainCell } from "@/components/data/ManagementList";
import { useDialogParams, useLookup } from "@/components/data/useListState";
import { runConfirmed, useEntityMutation } from "@/components/data/useEntityMutation";
import { formErrorText } from "@/components/data/formHelpers";
import { addDaysISO, formatDate, formatDateTime, formatPeriod, formatTime, todayISO } from "@/lib/formatting/date";
import { formatMoney } from "@/lib/formatting/money";
import { GENDER_LABELS, PAYMENT_METHOD_LABELS } from "@/lib/formatting/labels";
import { errorMessage } from "@/lib/http/apiError";
import { openHtmlDocument } from "@/lib/http/document";
import { toList } from "@/lib/http/page";
import { studentsService } from "@/features/students/service/studentsService";
import { parentsService } from "@/features/parents/service/parentsService";
import { assignmentsService } from "@/features/assignments/service/assignmentsService";
import { attendanceService } from "@/features/attendance/service/attendanceService";
import { routesService } from "@/features/routes/service/routesService";
import { paymentsService } from "@/features/payments/service/paymentsService";
import { contractsService } from "@/features/contracts/service/contractsService";
import { studentKeys } from "@/features/students/hooks/keys";
import { RELATION_OPTIONS, linkSchema, relationLabel } from "@/features/students/schemas/studentSchema";
import { StudentFormDialog, useDeactivateStudent } from "./StudentManagementScreen";
import StudentAccountCard from "./StudentAccountCard";
import styles from "../style/students.module.scss";

const TABS = [
  { key: "bilgiler", label: "Bilgiler", icon: "pi pi-id-card" },
  { key: "veliler", label: "Veliler", icon: "pi pi-users" },
  { key: "atamalar", label: "Atamalar", icon: "pi pi-directions" },
  { key: "yoklama", label: "Yoklama geçmişi", icon: "pi pi-history" },
  { key: "finans", label: "Sözleşme ve ekstre", icon: "pi pi-wallet" },
];

/** /ogrenciler/[id] (ADMIN) — öğrenci detayı, `?tab=` sekmeleri. ADMIN değilse /ogrenciler'e yönlenir. */
export default function StudentDetailScreen({ id }) {
  const { role } = useAuth();
  if (role && role !== "ADMIN") return <RoleRedirect to="/ogrenciler" />;
  return (
    <Suspense fallback={<ScreenState kind="loading" />}>
      <StudentDetail id={id} />
    </Suspense>
  );
}

function StudentDetail({ id }) {
  const query = useQuery({ queryKey: [...studentKeys.detail(), String(id)], queryFn: () => studentsService.get(id) });
  const dlg = useDialogParams();
  const deactivate = useDeactivateStudent();
  const student = query.data;
  const tab = TABS.some((t) => t.key === dlg.get("tab")) ? dlg.get("tab") : "bilgiler";
  const activeIndex = TABS.findIndex((t) => t.key === tab);

  return (
    <>
      <PageMeta title={student?.fullName ?? "Öğrenci"} crumb="Yönetim / Öğrenciler" back="/ogrenciler" />
      <QueryState query={query} forbiddenHome="/ogrenciler">
        {(s) => (
          <div className="stack-lg">
            <section className={styles.head}>
              <div className={styles.headMain}>
                <Avatar name={s.fullName} size={52} />
                <div className={styles.headText}>
                  <h2 className={styles.headTitle}>{s.fullName}</h2>
                  <div className="text-sm muted">{[s.schoolName, s.grade].filter(Boolean).join(" · ") || "Okul belirtilmedi"}</div>
                </div>
                <ActiveBadge value={s.isActive} />
              </div>
              <div className="row">
                <Button label="Düzenle" icon="pi pi-pencil" severity="secondary" outlined onClick={() => dlg.openEdit(s.id)} />
                {s.isActive && <Button label="Pasife al" text severity="danger" onClick={() => deactivate(s)} />}
              </div>
              <TabMenu
                className={styles.tabs}
                model={TABS.map((t) => ({ label: t.label, icon: t.icon }))}
                activeIndex={activeIndex}
                onTabChange={(e) => dlg.setParam("tab", TABS[e.index].key === "bilgiler" ? null : TABS[e.index].key)}
              />
            </section>
            {tab === "bilgiler" && <InfoTab s={s} />}
            {tab === "veliler" && <ParentsTab student={s} />}
            {tab === "atamalar" && <AssignmentsTab student={s} />}
            {tab === "yoklama" && <AttendanceTab student={s} />}
            {tab === "finans" && <FinanceTab student={s} />}
          </div>
        )}
      </QueryState>
      <StudentFormDialog visible={Boolean(dlg.editId)} editId={dlg.editId} fromList={student && String(student.id) === dlg.editId ? student : undefined} onHide={dlg.close} />
    </>
  );
}

function InfoTab({ s }) {
  const address = [s.addressLine, s.neighborhood, s.district, s.city].filter(Boolean).join(", ");
  return (
    <div className="stack-lg">
      <Card title="Öğrenci bilgileri">
        <DefinitionList
          items={[
            ["Doğum tarihi", formatDate(s.birthDate)],
            ["T.C. kimlik", <span key="tc" className="mono">{s.tcNo || "—"}</span>],
            ["Okul", s.schoolName || "—"],
            ["Sınıf", s.grade || "—"],
            ["Cinsiyet", GENDER_LABELS[s.gender] ?? "Belirtilmedi"],
            ["Adres", address || "—"],
            ["Sağlık notu", s.healthNote || "—"],
          ]}
        />
      </Card>
      <StudentAccountCard student={s} />
    </div>
  );
}

/* ───────── Veliler ───────── */

function ParentsTab({ student }) {
  const linksQuery = useQuery({ queryKey: studentKeys.links(student.id), queryFn: () => parentsService.links({ studentId: student.id }) });
  const parents = useLookup(["parents"], () => parentsService.list({ size: 100, sort: "fullName,asc" }), { label: (p) => `${p.fullName}${p.phone ? ` · ${p.phone}` : ""}` });
  const [open, setOpen] = useState(false);
  const revoke = useEntityMutation((linkId) => parentsService.revokeLink(linkId), [["parent-links"]]);
  const parentOf = (pid) => parents.byId.get(String(pid));

  const remove = (l) =>
    runConfirmed(
      {
        title: "Bağı kaldır",
        message: `${parentOf(l.parentId)?.fullName ?? "Veli"} artık ${student.fullName} öğrencisinin verilerine erişemez. Kayıt silinmez, geçmişte görünür.`,
        acceptLabel: "Bağı kaldır",
        success: "Bağ kaldırıldı",
      },
      () => revoke.mutateAsync(l.id)
    );

  return (
    <>
      <ManagementList
        title="Veli bağlantıları"
        query={linksQuery}
        paginated={false}
        actions={<Button label="Veli bağla" icon="pi pi-link" onClick={() => setOpen(true)} />}
        emptyIcon="pi-users"
        emptyTitle="Bağlı veli yok"
        emptyMessage="“Veli bağla” ile veli–öğrenci bağı kurun."
        columns={[
          {
            key: "parent",
            header: "Veli",
            main: true,
            body: (l) => <MainCell title={parentOf(l.parentId)?.fullName ?? `Veli #${l.parentId}`} sub={parentOf(l.parentId)?.phone} href={`/veliler/${l.parentId}`} />,
          },
          { key: "rel", header: "Yakınlık", body: (l) => relationLabel(l.relationType) },
          {
            key: "status",
            header: "Durum",
            body: (l) => (l.isActive ? <ActiveBadge value /> : <StatusBadge tone="muted" icon="pi-ban" label={`Kaldırıldı ${l.revokedAt ? formatDate(l.revokedAt) : ""}`} />),
          },
        ]}
        rowActions={(l) => (l.isActive ? <Button label="Bağı kaldır" size="small" text severity="danger" onClick={() => remove(l)} /> : null)}
      />
      {open && <LinkParentDialog student={student} options={parents.options} loading={parents.query.isPending} onHide={() => setOpen(false)} />}
    </>
  );
}

function LinkParentDialog({ student, options, loading, onHide }) {
  const [serverError, setServerError] = useState(null);
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(linkSchema), defaultValues: { parentId: "", studentId: String(student.id), relationType: "" } });
  const create = useEntityMutation((body) => parentsService.createLink(body), [["parent-links"]]);
  const onSubmit = handleSubmit(async (v) => {
    setServerError(null);
    try {
      await create.mutateAsync({ parentId: Number(v.parentId), studentId: Number(v.studentId), relationType: v.relationType });
      toastSuccess("Bağ kuruldu");
      onHide();
    } catch (err) {
      setServerError(formErrorText(err, setError, "Bağ kurulamadı."));
    }
  });
  return (
    <AppDialog visible onHide={onHide} title="Veli–öğrenci bağı kur" subtitle={student.fullName} icon="pi-link" tone="ok" submitLabel="Bağla" onSubmit={onSubmit} submitting={create.isPending} formId="student-link-form">
      {serverError && <Notice tone="danger" title="Bağ kurulamadı">{serverError}</Notice>}
      <Field label="Veli" error={errors.parentId?.message} required htmlFor="sl-parent" help="Veli profili yoksa önce Veliler ekranından ekleyin.">
        <Controller
          name="parentId"
          control={control}
          render={({ field }) => (
            <Dropdown inputId="sl-parent" value={field.value} options={options} placeholder={loading ? "Yükleniyor…" : "Veli seçin"} filter emptyMessage="Veli yok" onChange={(e) => field.onChange(e.value ?? "")} />
          )}
        />
      </Field>
      <Field label="Yakınlık" error={errors.relationType?.message} required htmlFor="sl-rel">
        <Controller
          name="relationType"
          control={control}
          render={({ field }) => <Dropdown inputId="sl-rel" value={field.value} options={RELATION_OPTIONS} placeholder="Seçin" onChange={(e) => field.onChange(e.value ?? "")} />}
        />
      </Field>
      <p className="text-xs muted">Aynı veli–öğrenci çifti ikinci kez bağlanamaz.</p>
    </AppDialog>
  );
}

/* ───────── Atamalar ───────── */

function StopLabel({ routeId, stopId }) {
  const q = useQuery({ queryKey: ["routes", "stops", String(routeId)], queryFn: () => routesService.stops(routeId), staleTime: 60_000 });
  const stop = toList(q.data).find((x) => String(x.id) === String(stopId));
  if (q.isPending) return <span className="muted">…</span>;
  return stop ? (
    <span>
      {stop.sequenceNo}. {stop.name} <span className="mono text-xs muted">{formatTime(stop.plannedTime)}</span>
    </span>
  ) : (
    <span className="muted">Durak #{stopId}</span>
  );
}

function AssignmentsTab({ student }) {
  const query = useQuery({ queryKey: studentKeys.assignments(student.id), queryFn: () => assignmentsService.list({ studentId: student.id, size: 100, sort: "validFrom,desc" }) });
  const routes = useLookup(["routes"], () => routesService.list({ size: 100, sort: "name,asc" }), { label: (r) => r.name });
  const newHref = `/atamalar?yeni=1&ogrenci=${student.id}`;
  return (
    <ManagementList
      title="Güzergâh atamaları"
      query={query}
      paginated={false}
      actions={
        <Link href={newHref} className="p-button p-button-sm">
          <i className="pi pi-plus" aria-hidden="true" style={{ marginRight: 6 }} />
          Atama ekle
        </Link>
      }
      emptyIcon="pi-directions"
      emptyTitle="Atama yok"
      emptyMessage="Öğrencinin servise binebilmesi için güzergâh ve durak ataması yapın."
      columns={[
        { key: "dir", header: "Yön", body: (a) => <StatusBadge group="direction" value={a.direction} /> },
        { key: "route", header: "Güzergâh", main: true, body: (a) => <MainCell title={routes.byId.get(String(a.routeId))?.name ?? `Güzergâh #${a.routeId}`} href={`/guzergahlar/${a.routeId}`} /> },
        { key: "stop", header: "Durak", body: (a) => <StopLabel routeId={a.routeId} stopId={a.stopId} /> },
        { key: "valid", header: "Geçerlilik", body: (a) => `${formatDate(a.validFrom)} – ${a.validTo ? formatDate(a.validTo) : "süresiz"}` },
      ]}
      rowActions={(a) => (
        <Link href={`/atamalar?duzenle=${a.id}`} className="p-button p-button-text p-button-sm">
          Düzenle
        </Link>
      )}
    />
  );
}

/* ───────── Yoklama geçmişi ───────── */

const RANGES = [
  { value: 7, label: "Son 7 gün" },
  { value: 30, label: "Son 30 gün" },
  { value: 90, label: "Son 90 gün" },
];

function AttendanceTab({ student }) {
  const [days, setDays] = useState(30);
  const { from, to } = useMemo(() => {
    const today = todayISO();
    return { from: `${addDaysISO(today, -days)}T00:00:00Z`, to: `${addDaysISO(today, 1)}T00:00:00Z` };
  }, [days]);
  const query = useQuery({
    queryKey: studentKeys.attendance(student.id, from, to),
    queryFn: () => attendanceService.forStudent(student.id, { from, to }),
  });
  const sorted = useMemo(() => ({ ...query, data: [...toList(query.data)].sort((a, b) => String(b.recordedAt).localeCompare(String(a.recordedAt))) }), [query]);
  return (
    <ManagementList
      title="Yoklama geçmişi"
      query={sorted}
      paginated={false}
      filters={
        <div className={styles.rangeField}>
          <label htmlFor="att-range" className="text-xs">
            Dönem
          </label>
          <Dropdown inputId="att-range" value={days} options={RANGES} onChange={(e) => setDays(e.value)} />
        </div>
      }
      emptyIcon="pi-history"
      emptyTitle="Bu dönemde yoklama kaydı yok"
      columns={[
        { key: "time", header: "Tarih", main: true, body: (e) => <MainCell title={formatDateTime(e.recordedAt)} sub={`Sefer #${e.tripId}`} href={`/seferler/${e.tripId}`} /> },
        { key: "type", header: "Kayıt", body: (e) => <StatusBadge group="attendanceEvent" value={e.eventType} /> },
        { key: "reason", header: "Not", body: (e) => e.reason || "—" },
      ]}
    />
  );
}

/* ───────── Finans ───────── */

function FinanceTab({ student }) {
  const statement = useQuery({ queryKey: studentKeys.statement(student.id), queryFn: () => studentsService.statement(student.id) });
  const payments = useQuery({ queryKey: studentKeys.payments(student.id), queryFn: () => paymentsService.list({ studentId: student.id, size: 50 }) });
  const contracts = useQuery({ queryKey: studentKeys.contracts(student.id), queryFn: () => contractsService.list({ studentId: student.id, size: 20 }) });
  const [opening, setOpening] = useState(false);
  const contract = toList(contracts.data).find((c) => c.status === "ACTIVE") ?? toList(contracts.data)[0];
  const balance = statement.data?.currentBalance;
  const entries = useMemo(() => ({ ...statement, data: toList(statement.data?.entries).map((e, i) => ({ ...e, _k: i })) }), [statement]);

  const openDoc = async (path) => {
    setOpening(true);
    try {
      await openHtmlDocument(path);
    } catch (err) {
      toastError("Belge açılamadı", errorMessage(err, "Belge açılamadı."));
    } finally {
      setOpening(false);
    }
  };

  return (
    <div className="stack-lg">
      <div className="grid-3">
        <MetricCard
          label="Güncel borç"
          icon="pi-wallet"
          value={statement.isPending ? "…" : formatMoney(balance)}
          hint={Number(balance) > 0 ? "Ödeme bekleniyor" : "Borç yok"}
          attention={Number(balance) > 0}
        />
        <MetricCard
          label="Sözleşme"
          icon="pi-file"
          value={contract ? `${formatMoney(contract.monthlyFee)} / ay` : "—"}
          hint={contract ? `${formatDate(contract.startDate)} – ${contract.endDate ? formatDate(contract.endDate) : "süresiz"}` : "Sözleşme yok"}
        />
        <Card title="Belgeler">
          <div className="stack">
            <Button label="Ekstreyi aç / yazdır" icon="pi pi-print" severity="secondary" outlined loading={opening} onClick={() => openDoc(studentsService.statementDocumentPath(student.id))} />
            <span className="text-xs muted">HTML belge yeni sekmede açılır; tarayıcıdan yazdırılabilir.</span>
          </div>
        </Card>
      </div>
      <div className="grid-2">
        <ManagementList
          title="Ekstre"
          query={entries}
          paginated={false}
          dataKey="_k"
          emptyIcon="pi-file"
          emptyTitle="Hareket yok"
          columns={[
            { key: "date", header: "Tarih", body: (e) => formatDate(e.occurredAt) },
            {
              key: "type",
              header: "Hareket",
              main: true,
              body: (e) =>
                e.type === "CHARGE" ? (
                  <MainCell title="Tahakkuk" sub={formatPeriod(e.period)} />
                ) : (
                  <MainCell title="Ödeme" sub={`${PAYMENT_METHOD_LABELS[e.method] ?? "—"}${e.paymentStatus === "CANCELLED" ? " · iptal" : ""}`} />
                ),
            },
            {
              key: "amount",
              header: "Tutar",
              align: "right",
              body: (e) => (
                <span className={`mono ${e.paymentStatus === "CANCELLED" ? styles.struck : ""}`}>
                  {e.type === "PAYMENT" ? "−" : ""}
                  {formatMoney(e.amount)}
                </span>
              ),
            },
            { key: "bal", header: "Bakiye", align: "right", body: (e) => <b className="mono">{formatMoney(e.runningBalance)}</b> },
          ]}
        />
        <ManagementList
          title="Ödemeler"
          query={payments}
          paginated={false}
          actions={
            <Link href={`/odemeler?yeni=1&ogrenci=${student.id}`} className="p-button p-button-sm">
              <i className="pi pi-plus" aria-hidden="true" style={{ marginRight: 6 }} />
              Ödeme kaydet
            </Link>
          }
          emptyIcon="pi-wallet"
          emptyTitle="Ödeme yok"
          columns={[
            { key: "date", header: "Tarih", body: (p) => formatDateTime(p.createdAt) },
            { key: "amount", header: "Tutar", main: true, body: (p) => <MainCell title={formatMoney(p.amount)} sub={PAYMENT_METHOD_LABELS[p.method]} /> },
            { key: "status", header: "Durum", body: (p) => <StatusBadge group="paymentStatus" value={p.status} /> },
          ]}
          rowActions={(p) =>
            p.status === "COMPLETED" ? <Button label="Makbuz" icon="pi pi-receipt" size="small" text onClick={() => openDoc(paymentsService.receiptPath(p.id))} /> : null
          }
        />
      </div>
    </div>
  );
}
