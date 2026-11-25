"use client";

import { useEffect, useId, useState } from "react";
import { InputText } from "primereact/inputtext";
import { Dropdown } from "primereact/dropdown";
import { useDebouncedValue } from "./useListState";
import styles from "./ManagementList.module.scss";

/** Filtre çubuğunda etiketli alan. */
export function FilterField({ label, htmlFor, children, grow = false }) {
  return (
    <div className={`${styles.filter} ${grow ? styles.filterGrow : ""}`}>
      <label className={styles.filterLabel} htmlFor={htmlFor}>
        {label}
      </label>
      {children}
    </div>
  );
}

/** Metin araması (gecikmeli). `onChange` 350 ms sonra çağrılır. */
export function SearchFilter({ label = "Ara", placeholder = "Yazın…", value = "", onChange }) {
  const id = useId();
  const [text, setText] = useState(value);
  const debounced = useDebouncedValue(text);
  useEffect(() => {
    if ((debounced || "") !== (value || "")) onChange?.(debounced.trim());
    // value dışarıdan değişirse tekrar tetiklenmesin
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);
  return (
    <FilterField label={label} htmlFor={id} grow>
      <span className={styles.search}>
        <i className="pi pi-search" aria-hidden="true" />
        <InputText id={id} type="search" value={text} placeholder={placeholder} onChange={(e) => setText(e.target.value)} />
      </span>
    </FilterField>
  );
}

/** Seçim filtresi; boş = "Tümü". options: [{value,label}] */
export function SelectFilter({ label, value, onChange, options, placeholder = "Tümü", filter = false, loading = false }) {
  const id = useId();
  return (
    <FilterField label={label} htmlFor={id}>
      <Dropdown
        inputId={id}
        value={value ?? ""}
        options={[{ value: "", label: placeholder }, ...(options ?? [])]}
        optionLabel="label"
        optionValue="value"
        onChange={(e) => onChange?.(e.value ?? "")}
        filter={filter}
        filterPlaceholder="Ara"
        emptyFilterMessage="Sonuç yok"
        emptyMessage={loading ? "Yükleniyor…" : "Seçenek yok"}
        className={styles.select}
      />
    </FilterField>
  );
}

const ACTIVE_OPTIONS = [
  { value: "true", label: "Aktif" },
  { value: "false", label: "Pasif" },
];

/** Aktif / pasif durum filtresi (`isActive`). */
export function ActiveFilter({ value, onChange, label = "Durum" }) {
  return <SelectFilter label={label} value={value} onChange={onChange} options={ACTIVE_OPTIONS} />;
}
