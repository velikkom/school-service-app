package com.okulservisi.backend.contract.entity;

/** docs/API.md enum sözlüğü. */
public enum ContractStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED,
    /** 2026-10-25, veli öz-kayıt sihirbazı (bkz. docs/API.md §19) — ADMIN {@code approve}/{@code
     * reject} edene kadar bu durumda kalır, sözleşme/öğrenci henüz "aktif" sayılmaz. */
    PENDING_APPROVAL
}
