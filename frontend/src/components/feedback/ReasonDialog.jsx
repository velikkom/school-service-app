"use client";

import { useId, useState } from "react";
import { InputTextarea } from "primereact/inputtextarea";
import AppDialog from "@/components/feedback/AppDialog";
import Notice from "@/components/ui/Notice";
import styles from "@/components/ui/Field.module.scss";

/**
 * Gerekçe zorunlu (veya isteğe bağlı) işlem dialogu: ret, iptal, iptal talebi kararı vb.
 * Boş gönderimde satır içi hata gösterir; `onConfirm(reason)` Promise döndürebilir.
 */
export default function ReasonDialog({
  visible,
  onHide,
  title,
  subtitle,
  icon = "pi-times-circle",
  tone = "danger",
  label = "Gerekçe",
  placeholder = "Kısaca açıklayın",
  required = true,
  maxLength = 500,
  confirmLabel = "Gönder",
  confirmSeverity = "danger",
  warning,
  children,
  onConfirm,
  submitting = false,
}) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState(null);
  const fieldId = useId();

  // Dialog her açılışta temiz başlar (render sırasında önceki görünürlükle karşılaştırma).
  const [wasVisible, setWasVisible] = useState(visible);
  if (visible !== wasVisible) {
    setWasVisible(visible);
    if (visible) {
      setReason("");
      setError(null);
    }
  }

  const submit = async () => {
    const value = reason.trim();
    if (required && !value) {
      setError(`${label} zorunludur.`);
      return;
    }
    setError(null);
    await onConfirm?.(value);
  };

  return (
    <AppDialog
      visible={visible}
      onHide={onHide}
      title={title}
      subtitle={subtitle}
      icon={icon}
      tone={tone}
      submitLabel={confirmLabel}
      submitSeverity={confirmSeverity}
      onSubmit={submit}
      submitting={submitting}
    >
      {warning && <Notice tone={warning.tone ?? "warn"} title={warning.title}>{warning.text}</Notice>}
      {children}
      <div className={styles.field}>
        <label htmlFor={fieldId} className={styles.label}>
          {label} {required && <span className={styles.req}>*</span>}
        </label>
        <InputTextarea
          id={fieldId}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={placeholder}
          maxLength={maxLength}
          rows={4}
          autoResize={false}
          className={error ? "p-invalid" : undefined}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${fieldId}-err` : undefined}
          autoFocus
        />
        {error ? (
          <span id={`${fieldId}-err`} className={styles.error} role="alert">
            {error}
          </span>
        ) : (
          <span className={styles.help}>
            {reason.length}/{maxLength}
          </span>
        )}
      </div>
    </AppDialog>
  );
}
