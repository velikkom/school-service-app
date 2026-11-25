"use client";

import Link from "next/link";
import { Button } from "primereact/button";
import styles from "./ScreenState.module.scss";

/**
 * Ortak ekran durumları (DESIGN_SYSTEM.md §6 "Ekran durumları").
 * kind: "loading" | "empty" | "error" | "forbidden" | "notFound"
 */
export default function ScreenState({
  kind = "empty",
  title,
  message,
  icon,
  error,
  onRetry,
  action,
  homeHref,
  rows = 4,
  compact = false,
  bare = false,
}) {
  const wrap = (content) => (bare ? content : <div className={styles.card}>{content}</div>);

  if (kind === "loading") {
    return wrap(
      <div className={styles.loading} aria-busy="true" role="status">
        <div className={styles.skTitle} />
        {Array.from({ length: rows }).map((_, i) => (
          <div className={styles.skRow} key={i}>
            <div className={styles.skIcon} />
            <div className={styles.skLines}>
              <div className={styles.skLine} style={{ width: "70%" }} />
              <div className={styles.skLine} style={{ width: "45%" }} />
            </div>
          </div>
        ))}
        <span className="sr-only">{title || "Yükleniyor"}</span>
      </div>
    );
  }

  const preset = {
    empty: { icon: "pi-inbox", title: "Henüz kayıt yok", message: "Bu listede gösterilecek kayıt bulunmuyor.", tone: styles.neutral },
    error: {
      icon: "pi-exclamation-circle",
      title: "Veriler yüklenemedi",
      message: "Sunucuya ulaşılamadı veya beklenmeyen bir hata oluştu. Bağlantınızı kontrol edip tekrar deneyin.",
      tone: styles.error,
    },
    forbidden: {
      icon: "pi-lock",
      title: "Bu sayfaya erişim yetkiniz yok",
      message: "Bu işlem rolünüz için açık değil ya da kayıt sizin kapsamınızda değil. Bir hata olduğunu düşünüyorsanız kurum yöneticisiyle iletişime geçin.",
      tone: styles.forbid,
    },
    notFound: {
      icon: "pi-search",
      title: "Kayıt bulunamadı",
      message: "Aradığınız kayıt yok ya da sizin kapsamınızda değil.",
      tone: styles.forbid,
    },
  }[kind] ?? {};

  const errorCode = kind === "error" && error?.status ? `Hata kodu: ${error.status}${error.error ? ` · ${error.error}` : ""}` : null;

  return wrap(
    <div className={`${styles.state} ${preset.tone ?? ""} ${compact ? styles.compact : ""}`} role={kind === "error" ? "alert" : undefined}>
      <div className={styles.ill} aria-hidden="true">
        <i className={`pi ${icon || preset.icon}`} />
      </div>
      <h3 className={styles.title}>{title || preset.title}</h3>
      {(message || preset.message) && <p className={styles.message}>{message || preset.message}</p>}
      {errorCode && <div className={`${styles.code} mono`}>{errorCode}</div>}
      <div className={styles.actions}>
        {kind === "error" && onRetry && <Button type="button" icon="pi pi-refresh" label="Tekrar dene" onClick={() => onRetry()} />}
        {(kind === "forbidden" || kind === "notFound") && homeHref && (
          <Link href={homeHref} className="p-button p-button-secondary">
            Ana sayfaya dön
          </Link>
        )}
        {action}
      </div>
    </div>
  );
}

/** TanStack sorgusunu ortak durumlara bağlar: 403 → yetki yok, 404 → bulunamadı/boş. */
export function QueryState({ query, empty, isEmpty, loadingRows, notFound, forbiddenHome, children }) {
  if (query.isPending && !query.data) return <ScreenState kind="loading" rows={loadingRows} />;
  if (query.isError && !query.data) {
    const status = query.error?.status;
    if (status === 403) return <ScreenState kind="forbidden" homeHref={forbiddenHome ?? "/dashboard"} />;
    if (status === 404) return notFound ?? <ScreenState kind="notFound" homeHref={forbiddenHome ?? "/dashboard"} />;
    return <ScreenState kind="error" error={query.error} onRetry={query.refetch} />;
  }
  if (isEmpty && isEmpty(query.data)) return empty ?? <ScreenState kind="empty" />;
  return children(query.data);
}
