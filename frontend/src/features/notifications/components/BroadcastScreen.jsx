"use client";

import { useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { MultiSelect } from "primereact/multiselect";
import { SelectButton } from "primereact/selectbutton";
import PageMeta from "@/components/layout/PageMeta";
import Card from "@/components/ui/Card";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { confirmAction } from "@/components/feedback/feedback";
import { parentsService } from "@/features/parents/service/parentsService";
import { studentsService } from "@/features/students/service/studentsService";
import { applyServerFieldErrors } from "@/lib/http/applyServerErrors";
import { toList } from "@/lib/http/page";
import { broadcastSchema, toBroadcastBody } from "../schemas/broadcastSchema";
import { useBroadcast } from "../action/useBroadcast";
import styles from "../style/notifications.module.scss";

const PICKER_LIMIT = 100;

const TARGET_OPTIONS = [
  { value: "ROLE", label: "Role göre" },
  { value: "PARENTS", label: "Seçili veliler" },
  { value: "STUDENTS", label: "Seçili öğrenciler" },
];

const ROLE_TARGETS = [
  { value: "PARENT", label: "Tüm veliler" },
  { value: "STUDENT", label: "Tüm öğrenciler" },
  { value: "DRIVER", label: "Tüm şoförler" },
  { value: "HOSTESS", label: "Tüm hostesler" },
  { value: "ADMIN", label: "Tüm yöneticiler" },
];

const DEFAULTS = { target: "ROLE", targetRole: "PARENT", parentIds: [], studentIds: [], title: "", body: "" };

function audienceText(v) {
  if (v.target === "ROLE") return `${ROLE_TARGETS.find((r) => r.value === v.targetRole)?.label.toLocaleLowerCase("tr") ?? "seçili rol"} (aktif kullanıcılar)`;
  if (v.target === "PARENTS") return `seçili ${v.parentIds.length} veli`;
  return `seçili ${v.studentIds.length} öğrenci ve bu öğrencilerin bağlı velileri`;
}

/** ADMIN "Toplu Bildirim" (/bildirimler/toplu): hedef, başlık, mesaj; geri alınamaz onay; alıcı sayısı. */
export default function BroadcastScreen() {
  const [result, setResult] = useState(null);
  const broadcast = useBroadcast();
  const {
    control,
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(broadcastSchema), defaultValues: DEFAULTS });
  const target = useWatch({ control, name: "target" });

  const parents = useQuery({
    queryKey: ["parents", "list", { size: PICKER_LIMIT }],
    queryFn: () => parentsService.list({ size: PICKER_LIMIT }),
    enabled: target === "PARENTS",
    staleTime: 5 * 60_000,
  });
  const students = useQuery({
    queryKey: ["students", "list", { size: PICKER_LIMIT }],
    queryFn: () => studentsService.list({ size: PICKER_LIMIT }),
    enabled: target === "STUDENTS",
    staleTime: 5 * 60_000,
  });

  const parentOptions = toList(parents.data).map((p) => ({ value: p.id, label: p.phone ? `${p.fullName} · ${p.phone}` : p.fullName }));
  const studentOptions = toList(students.data)
    .filter((s) => s.isActive !== false)
    .map((s) => ({ value: s.id, label: s.schoolName ? `${s.fullName} · ${s.schoolName}` : s.fullName }));
  const truncated = (q) => (q.data?.totalElements ?? 0) > PICKER_LIMIT;

  const submit = handleSubmit(async (values) => {
    const ok = await confirmAction({
      title: "Duyuruyu gönder",
      message: `"${values.title}" başlıklı duyuru ${audienceText(values)} gönderilecek. Gönderilen bildirim geri alınamaz.`,
      acceptLabel: "Gönder",
      tone: "danger",
    });
    if (!ok) return;
    try {
      const resp = await broadcast.mutateAsync(toBroadcastBody(values));
      setResult({ count: resp?.recipientCount ?? 0, title: values.title });
      reset(DEFAULTS);
    } catch (err) {
      applyServerFieldErrors(err, setError);
    }
  });

  return (
    <>
      <PageMeta title="Toplu Bildirim" back="/bildirimler" />
      <div className={`stack-lg ${styles.form}`}>
        {result && (
          <Notice tone="ok" title="Duyuru gönderildi">
            &quot;{result.title}&quot; {result.count} alıcıya iletildi.
          </Notice>
        )}
        <Card title="Yeni duyuru">
          <form noValidate onSubmit={submit} className="stack-lg">
            <Field label="Hedef kitle" required>
              <Controller
                name="target"
                control={control}
                render={({ field }) => (
                  <SelectButton
                    className={styles.segment}
                    value={field.value}
                    options={TARGET_OPTIONS}
                    onChange={(e) => e.value && field.onChange(e.value)}
                    allowEmpty={false}
                    aria-label="Hedef kitle"
                  />
                )}
              />
            </Field>

            {target === "ROLE" && (
              <Field label="Rol" required error={errors.targetRole?.message} htmlFor="bc-role">
                <Controller
                  name="targetRole"
                  control={control}
                  render={({ field }) => (
                    <Dropdown inputId="bc-role" value={field.value} options={ROLE_TARGETS} onChange={(e) => field.onChange(e.value)} />
                  )}
                />
              </Field>
            )}

            {target === "PARENTS" && (
              <Field
                label="Veliler"
                required
                htmlFor="bc-parents"
                error={errors.parentIds?.message || (parents.isError ? "Veli listesi alınamadı." : undefined)}
                help={truncated(parents) ? `İlk ${PICKER_LIMIT} veli listelendi; aramayla daraltın.` : "Ada veya telefona göre arayın"}
              >
                <Controller
                  name="parentIds"
                  control={control}
                  render={({ field }) => (
                    <MultiSelect
                      inputId="bc-parents"
                      value={field.value}
                      options={parentOptions}
                      onChange={(e) => field.onChange(e.value ?? [])}
                      filter
                      display="chip"
                      placeholder={parents.isPending ? "Yükleniyor…" : "Veli seçin"}
                      emptyFilterMessage="Eşleşen veli yok"
                      emptyMessage="Veli bulunamadı"
                      maxSelectedLabels={6}
                      selectedItemsLabel="{0} veli seçildi"
                    />
                  )}
                />
              </Field>
            )}

            {target === "STUDENTS" && (
              <Field
                label="Öğrenciler"
                required
                htmlFor="bc-students"
                error={errors.studentIds?.message || (students.isError ? "Öğrenci listesi alınamadı." : undefined)}
                help={truncated(students) ? `İlk ${PICKER_LIMIT} öğrenci listelendi; aramayla daraltın.` : "Ada veya okula göre arayın"}
              >
                <Controller
                  name="studentIds"
                  control={control}
                  render={({ field }) => (
                    <MultiSelect
                      inputId="bc-students"
                      value={field.value}
                      options={studentOptions}
                      onChange={(e) => field.onChange(e.value ?? [])}
                      filter
                      display="chip"
                      placeholder={students.isPending ? "Yükleniyor…" : "Öğrenci seçin"}
                      emptyFilterMessage="Eşleşen öğrenci yok"
                      emptyMessage="Öğrenci bulunamadı"
                      maxSelectedLabels={6}
                      selectedItemsLabel="{0} öğrenci seçildi"
                    />
                  )}
                />
              </Field>
            )}

            <Field label="Başlık" required error={errors.title?.message}>
              <InputText maxLength={200} placeholder="Örn. Cuma günü servis saatleri" {...register("title")} />
            </Field>
            <Field label="Mesaj" required error={errors.body?.message}>
              <InputTextarea rows={5} autoResize={false} maxLength={4000} {...register("body")} />
            </Field>

            <Notice tone="info">Bildirim uygulama içinde ve e-postayla gönderilir. Gönderim sonrası benzersiz alıcı sayısı gösterilir.</Notice>

            <div className={styles.actions}>
              <Button type="submit" label="Gönder" icon="pi pi-send" loading={broadcast.isPending} disabled={broadcast.isPending} />
            </div>
          </form>
        </Card>
      </div>
    </>
  );
}
