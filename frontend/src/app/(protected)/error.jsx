"use client";

import ScreenState from "@/components/feedback/ScreenState";

/** Korumalı ekranlarda beklenmeyen istemci hatası sınırı. */
export default function ProtectedError({ error, reset }) {
  return (
    <ScreenState
      kind="error"
      title="Ekran yüklenemedi"
      message="Beklenmeyen bir hata oluştu. Tekrar deneyin; sorun sürerse sayfayı yenileyin."
      error={error?.digest ? { status: "İstemci", error: error.digest } : undefined}
      onRetry={reset}
    />
  );
}
