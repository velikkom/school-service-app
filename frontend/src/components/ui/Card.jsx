import Link from "next/link";
import styles from "./ui.module.scss";

/** Kart: isteğe bağlı başlık satırı (+ sağda aksiyonlar), gövde ve alt satır. */
export default function Card({ title, subtitle, actions, footer, children, padded = true, className, as: Tag = "section", ...rest }) {
  return (
    <Tag className={[styles.card, className].filter(Boolean).join(" ")} {...rest}>
      {(title || actions) && (
        <header className={styles.cardHead}>
          <div className={styles.cardHeadText}>
            {title && <h2 className={styles.cardTitle}>{title}</h2>}
            {subtitle && <div className={styles.cardSub}>{subtitle}</div>}
          </div>
          {actions && <div className={styles.cardActions}>{actions}</div>}
        </header>
      )}
      <div className={padded ? styles.cardBody : undefined}>{children}</div>
      {footer && <footer className={styles.cardFoot}>{footer}</footer>}
    </Tag>
  );
}

/** Metrik kartı: etiket + büyük sayı + ipucu; dikkat isteyen kartta sarı şerit. Tıklanınca listeye gider. */
export function MetricCard({ label, value, hint, icon, href, attention = false }) {
  const content = (
    <>
      <span className={styles.metricLabel}>
        {icon && <i className={`pi ${icon}`} aria-hidden="true" />}
        {label}
      </span>
      <span className={styles.metricValue}>{value ?? "—"}</span>
      {hint && <span className={styles.metricHint}>{hint}</span>}
    </>
  );
  const cls = [styles.card, styles.metric, attention ? styles.metricAttention : ""].join(" ");
  return href ? (
    <Link href={href} className={cls}>
      {content}
    </Link>
  ) : (
    <div className={cls}>{content}</div>
  );
}

/** Tanım listesi (etiket: değer). items: [[label, value], ...] */
export function DefinitionList({ items }) {
  return (
    <dl className={styles.dl}>
      {items
        .filter(Boolean)
        .map(([k, v]) => (
          <div key={k} className={styles.dlRow}>
            <dt>{k}</dt>
            <dd>{v ?? "—"}</dd>
          </div>
        ))}
    </dl>
  );
}
