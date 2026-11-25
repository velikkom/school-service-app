"use client";

import AppDialog from "@/components/feedback/AppDialog";
import ScreenState from "@/components/feedback/ScreenState";

/**
 * Düzenleme dialogu için kayıt kapısı: kayıt yüklenirken/yüklenemezken dialog içinde durum gösterir,
 * hazır olunca `children`'ı (asıl form dialogu) render eder. Form, kayıt hazırken mount edildiği için
 * varsayılan değerleri doğrudan kayıttan alır (effect içinde reset gerekmez).
 */
export default function RecordGate({
  visible,
  onHide,
  title,
  isLoading,
  error,
  children,
}) {
  if (!visible) return null;
  if (isLoading || error) {
    return (
      <AppDialog
        visible
        onHide={onHide}
        title={title}
        icon="pi-pencil"
        tone="info"
      >
        {error ? (
          <ScreenState
            kind={error.status === 404 ? "notFound" : "error"}
            bare
            compact
            error={error}
          />
        ) : (
          <ScreenState kind="loading" bare rows={2} title="Kayıt yükleniyor" />
        )}
      </AppDialog>
    );
  }
  return children;
}
