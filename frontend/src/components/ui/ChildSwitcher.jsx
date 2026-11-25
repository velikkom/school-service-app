"use client";

import Link from "next/link";
import { initials } from "@/lib/formatting/money";
import styles from "./ChildSwitcher.module.scss";

/**
 * Çocuk seçici (DESIGN_SYSTEM.md §6): yatay kaydırılabilir hap listesi, sonda kesik kenarlı
 * "Öğrenci ekle". Tek çocukta da gösterilir (hangi çocuğun verisi olduğu görünür kalsın).
 */
export default function ChildSwitcher({ items, selectedId, onSelect, showAdd = true }) {
  if (!items?.length) return null;
  return (
    <div className={styles.childSwitch} role="tablist" aria-label="Çocuk seçimi">
      {items.map((s) => {
        const on = s.id === selectedId;
        return (
          <button
            key={s.id}
            type="button"
            role="tab"
            aria-selected={on}
            className={`${styles.childPill} ${on ? styles.childOn : ""}`}
            onClick={() => !on && onSelect(s.id)}
          >
            <span className={styles.childAvatar} aria-hidden="true">
              {initials(s.fullName)}
            </span>
            <span className={styles.childName}>{s.fullName}</span>
            {s.grade && <span className={styles.childGrade}>{s.grade}</span>}
          </button>
        );
      })}
      {showAdd && (
        <Link href="/ogrenci-ekle" className={`${styles.childPill} ${styles.childAdd}`}>
          <i className="pi pi-plus" aria-hidden="true" />
          Öğrenci ekle
        </Link>
      )}
    </div>
  );
}
