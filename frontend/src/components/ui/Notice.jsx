import styles from "./ui.module.scss";

const ICONS = {
  info: "pi-info-circle",
  warn: "pi-exclamation-triangle",
  danger: "pi-times-circle",
  ok: "pi-check-circle",
  notice: "pi-flag",
  muted: "pi-info-circle",
};

/** Uyarı kutusu: info / warn / danger / ok / notice / muted. Başlık (kalın) + açıklama. */
export default function Notice({ tone = "info", title, children, icon, action, role }) {
  return (
    <div className={`${styles.notice} ${styles[`n-${tone}`]}`} role={role ?? (tone === "danger" ? "alert" : undefined)}>
      <i className={`pi ${icon ?? ICONS[tone]}`} aria-hidden="true" />
      <div className={styles.noticeBody}>
        {title && <b>{title}</b>}
        {children && <div>{children}</div>}
      </div>
      {action && <div className={styles.noticeAction}>{action}</div>}
    </div>
  );
}

/** Baş harfli yuvarlak avatar. */
export function Avatar({ name, size = 36 }) {
  const text = (name || "?")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toLocaleUpperCase("tr");
  return (
    <span className={styles.avatar} style={{ width: size, height: size, fontSize: Math.round(size * 0.36) }} aria-hidden="true">
      {text}
    </span>
  );
}
