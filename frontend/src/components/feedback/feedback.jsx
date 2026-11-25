"use client";

import { useEffect, useState } from "react";
import { Toast } from "primereact/toast";
import { ConfirmDialog, confirmDialog } from "primereact/confirmdialog";

let toastRef = null;

/** Kök sağlayıcıda bir kez mount edilir: toast + onay dialogu. */
export function FeedbackHost() {
  const [position, setPosition] = useState("bottom-right");
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 899.98px)");
    const update = () =>
      setPosition(mq.matches ? "bottom-center" : "bottom-right");
    update();
    mq.addEventListener("change", update);
    return () => mq.removeEventListener("change", update);
  }, []);

  return (
    <>
      <Toast
        ref={(el) => {
          toastRef = el;
        }}
        position={position}
        aria-live="polite"
      />
      <ConfirmDialog />
    </>
  );
}

export function toastSuccess(message, detail) {
  toastRef?.show({ severity: "success", summary: message, detail, life: 3600 });
}

export function toastError(message, detail) {
  toastRef?.show({ severity: "error", summary: message, detail, life: 5000 });
}

export function toastInfo(message, detail) {
  toastRef?.show({ severity: "info", summary: message, detail, life: 3600 });
}

/**
 * Onay dialogu (Promise<boolean>). Geri alınamaz işlemlerde tone="danger".
 * Dialog kapanınca odak, açılmadan önceki öğeye döner.
 */
export function confirmAction({
  title = "Emin misiniz?",
  message = "Bu işlem geri alınamaz.",
  acceptLabel = "Onayla",
  rejectLabel = "Vazgeç",
  tone = "danger",
} = {}) {
  const previouslyFocused =
    typeof document !== "undefined" ? document.activeElement : null;
  return new Promise((resolve) => {
    let done = false;
    const finish = (v) => {
      if (done) return;
      done = true;
      resolve(v);
      if (previouslyFocused && typeof previouslyFocused.focus === "function") {
        setTimeout(() => previouslyFocused.focus(), 0);
      }
    };
    confirmDialog({
      header: title,
      message,
      icon:
        tone === "danger" ? "pi pi-exclamation-triangle" : "pi pi-info-circle",
      acceptLabel,
      rejectLabel,
      acceptClassName: tone === "danger" ? "p-button-danger" : undefined,
      rejectClassName: "p-button-secondary",
      defaultFocus: "reject",
      accept: () => finish(true),
      reject: () => finish(false),
      onHide: () => finish(false),
    });
  });
}
