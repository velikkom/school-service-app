"use client";

import { cloneElement, isValidElement, useId } from "react";
import styles from "./Field.module.scss";

/**
 * Etiketli form alanı. Çocuk kontrol tek bir eleman olmalıdır; id/aria bağları otomatik verilir.
 * <Field label="Ad" error={errors.name?.message} required><InputText {...register("name")} /></Field>
 */
export default function Field({ label, error, help, required, children, full, className, htmlFor }) {
  const autoId = useId();
  const id = htmlFor || autoId;
  const describedBy = error ? `${id}-err` : help ? `${id}-help` : undefined;
  const control = isValidElement(children)
    ? cloneElement(children, {
        // PrimeReact Dropdown/Calendar/Password için çağıran `inputId` + `htmlFor` verir; burada DOM'a
        // bilinmeyen prop geçmemek için yalnızca `id` atanır.
        id: children.props.id ?? (children.props.inputId ? undefined : id),
        "aria-invalid": error ? true : undefined,
        "aria-describedby": describedBy,
        className: [children.props.className, error ? "p-invalid" : null].filter(Boolean).join(" ") || undefined,
      })
    : children;

  return (
    <div className={[styles.field, full ? styles.full : "", className ?? ""].join(" ")}>
      {label && (
        <label htmlFor={id} className={styles.label}>
          {label} {required && <span className={styles.req} aria-hidden="true">*</span>}
        </label>
      )}
      {control}
      {error ? (
        <span id={`${id}-err`} className={styles.error} role="alert">
          {error}
        </span>
      ) : help ? (
        <span id={`${id}-help`} className={styles.help}>
          {help}
        </span>
      ) : null}
    </div>
  );
}

/** İki sütunlu form ızgarası (mobilde tek sütun). */
export function FormGrid({ children }) {
  return <div className={styles.grid}>{children}</div>;
}
