import { labelOf } from "@/lib/formatting/labels";
import styles from "./ui.module.scss";

/**
 * Durum rozeti — her zaman metin + ikon (DESIGN_SYSTEM.md §2, §6).
 * <StatusBadge group="tripStatus" value="IN_PROGRESS" />  veya
 * <StatusBadge tone="warn" icon="pi-clock" label="Gecikme +10 dk" />
 */
export default function StatusBadge({ group, value, tone, icon, label, suffix, className }) {
  const meta = group ? labelOf(group, value) : { label, tone: tone ?? "muted", icon };
  const t = tone ?? meta.tone;
  return (
    <span className={[styles.badge, styles[`b-${t}`], className].filter(Boolean).join(" ")}>
      {t === "live" ? <span className={styles.pulse} aria-hidden="true" /> : meta.icon && <i className={`pi ${meta.icon}`} aria-hidden="true" />}
      <span>
        {label ?? meta.label}
        {suffix ? ` ${suffix}` : ""}
      </span>
    </span>
  );
}

/** Veli "servise binmeyecek" bildirimi — resmi yoklama değildir, mor tonda. */
export function NoticeBadge({ label = "Veli: binmeyecek" }) {
  return <StatusBadge tone="notice" icon="pi-flag" label={label} />;
}
