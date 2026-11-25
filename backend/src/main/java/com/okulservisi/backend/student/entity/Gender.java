package com.okulservisi.backend.student.entity;

/**
 * docs/DATABASE_DESIGN.md "Öğrenci / Veli" — {@code student.gender} (nullable, 2026-10-25'te veli
 * öz-kayıt sihirbazı için eklendi, bkz. docs/API.md §19). ADMIN'in doğrudan oluşturduğu öğrencilerde
 * {@code null} bırakılabilir; self-enrollment akışında uygulama seviyesinde zorunludur.
 */
public enum Gender {
    MALE,
    FEMALE
}
