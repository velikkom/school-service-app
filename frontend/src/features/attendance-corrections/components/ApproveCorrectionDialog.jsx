"use client";

import { useId, useState } from "react";
import { Dropdown } from "primereact/dropdown";
import { InputTextarea } from "primereact/inputtextarea";
import AppDialog from "@/components/feedback/AppDialog";
import Field from "@/components/ui/Field";
import Notice from "@/components/ui/Notice";
import { DefinitionList } from "@/components/ui/Card";
import { labelOf } from "@/lib/formatting/labels";
import { errorMessage } from "@/lib/http/apiError";
import { useApproveCorrection } from "@/features/attendance-corrections/action/useCorrectionActions";

const STATUSES = ["NONE", "BOARDED", "DROPPED_OFF", "ABSENT"];
const label = (s) => labelOf("attendance", s).label;

/**
 * Düzeltme onayı (POST /{id}/approve {newStatus?, note?}). Uygulanacak durum seçilebilir (varsayılan
 * istenen durum; veli incelemesinde zorunlu). 409 (kayıt değişmiş / zaten sonuçlanmış) satır içinde gösterilir.
 * Üst bileşen her talep için `key` ile yeniden kurar.
 */
export default function ApproveCorrectionDialog({ correction, onHide }) {
  const statusId = useId();
  const noteId = useId();
  const [newStatus, setNewStatus] = useState(correction?.requestedStatus ?? null);
  const [note, setNote] = useState("");
  const [fieldError, setFieldError] = useState(null);
  const [serverError, setServerError] = useState(null);
  const approve = useApproveCorrection();
  if (!correction) return null;

  const stale = Boolean(correction.stale);
  const options = STATUSES.filter((s) => s !== correction.currentStatus).map((s) => ({ value: s, label: label(s) }));

  const submit = async () => {
    setServerError(null);
    if (!newStatus) {
      setFieldError("Uygulanacak durumu seçin.");
      return;
    }
    setFieldError(null);
    try {
      await approve.mutateAsync({ correction, newStatus, note: note.trim() });
      onHide();
    } catch (err) {
      setServerError(errorMessage(err, "Onay kaydedilemedi."));
    }
  };

  return (
    <AppDialog
      visible
      onHide={onHide}
      title="Yoklama düzeltmesini onayla"
      subtitle={`${correction.studentFullName} · talep #${correction.id}`}
      icon="pi-check"
      tone="ok"
      submitLabel="Onayla ve uygula"
      submitIcon="pi-check"
      onSubmit={submit}
      submitting={approve.isPending}
      submitDisabled={stale}
      formId="correction-approve-form"
    >
      {stale && (
        <Notice tone="danger" title="Kayıt talepten sonra değişmiş">
          Talep anında: {label(correction.sourceStatus)} · şimdi: {label(correction.currentStatus)}. Onay sunucuda reddedilir; talebi
          reddedip yeniden açılmasını isteyin.
        </Notice>
      )}
      {serverError && (
        <Notice tone="danger" title="Onaylanamadı">
          {serverError}
        </Notice>
      )}
      <DefinitionList
        items={[
          ["Talep anındaki", label(correction.sourceStatus)],
          ["Güncel", label(correction.currentStatus)],
          ["İstenen", correction.requestedStatus ? label(correction.requestedStatus) : "Belirtilmedi (veli incelemesi)"],
        ]}
      />
      <Field label="Uygulanacak durum" required error={fieldError} htmlFor={statusId}>
        <Dropdown
          inputId={statusId}
          value={newStatus}
          options={options}
          placeholder="Seçin"
          onChange={(e) => {
            setNewStatus(e.value);
            setFieldError(null);
          }}
        />
      </Field>
      <Field label="Karar notu (isteğe bağlı)" help={`${note.length}/500`} htmlFor={noteId}>
        <InputTextarea id={noteId} rows={3} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
      </Field>
      <p className="text-xs muted" style={{ margin: 0 }}>
        Eski kayıtlar silinmez; geçersiz işaretlenir ve denetim kaydına yazılır. Talep sahibine bildirim gider.
      </p>
    </AppDialog>
  );
}
