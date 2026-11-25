"use client";

import { useEffect, useRef } from "react";
import { Dialog } from "primereact/dialog";
import { Button } from "primereact/button";
import { useIsMobile } from "@/components/ui/useIsMobile";
import styles from "./AppDialog.module.scss";

/**
 * Ortak dialog: başlıkta tonlu ikon kutusu, gövde, altta Vazgeç + aksiyon.
 * Mobilde alttan açılan sayfa. Esc ile kapanır, odak tuzağı PrimeReact'tedir; kapanınca odak
 * dialogu açan öğeye döner. `onSubmit` verilirse gövde bir <form> olur (Enter ile gönderim).
 */
export default function AppDialog({
  visible,
  onHide,
  title,
  subtitle,
  icon,
  tone = "info",
  children,
  submitLabel = "Kaydet",
  submitIcon,
  submitSeverity,
  cancelLabel = "Vazgeç",
  onSubmit,
  submitting = false,
  submitDisabled = false,
  hideFooter = false,
  wide = false,
  formId,
}) {
  const isMobile = useIsMobile();
  const opener = useRef(null);
  const id = formId || `dlg-form-${String(title || "x").replace(/\s+/g, "-").toLowerCase()}`;

  useEffect(() => {
    if (visible) opener.current = document.activeElement;
  }, [visible]);

  const handleHide = () => {
    if (submitting) return;
    onHide?.();
    const el = opener.current;
    if (el && typeof el.focus === "function") setTimeout(() => el.focus(), 0);
  };

  const header = (
    <div className={styles.head}>
      {icon && (
        <span className={`${styles.icon} ${styles[tone] ?? ""}`} aria-hidden="true">
          <i className={`pi ${icon}`} />
        </span>
      )}
      <div>
        <div className={styles.title}>{title}</div>
        {subtitle && <div className={styles.sub}>{subtitle}</div>}
      </div>
    </div>
  );

  const footer = hideFooter ? null : (
    <>
      <Button type="button" label={cancelLabel} className="p-button-secondary" onClick={handleHide} disabled={submitting} />
      {onSubmit && (
        <Button
          type="submit"
          form={id}
          label={submitLabel}
          icon={submitIcon ? `pi ${submitIcon}` : undefined}
          severity={submitSeverity}
          loading={submitting}
          disabled={submitDisabled || submitting}
        />
      )}
    </>
  );

  return (
    <Dialog
      visible={visible}
      onHide={handleHide}
      header={header}
      footer={footer}
      position={isMobile ? "bottom" : "center"}
      className={`sf-dialog ${styles.dialog} ${wide ? styles.wide : ""}`}
      draggable={false}
      resizable={false}
      dismissableMask={!submitting}
      closeOnEscape={!submitting}
      blockScroll
      focusOnShow
      ariaCloseIconLabel="Kapat"
    >
      {onSubmit ? (
        <form
          id={id}
          noValidate
          className={styles.body}
          onSubmit={(e) => {
            e.preventDefault();
            if (!submitting) onSubmit(e);
          }}
        >
          {children}
        </form>
      ) : (
        <div className={styles.body}>{children}</div>
      )}
    </Dialog>
  );
}
