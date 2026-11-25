package com.okulservisi.backend.student.api;

/** docs/API.md "3. Öğrenci" — {@code StudentController} mapping sabitleri. */
public final class StudentApiPaths {

    private StudentApiPaths() {
    }

    public static final String BASE = "/api/students";
    public static final String BY_ID = "/{id}";
    public static final String DEACTIVATE = "/{id}/deactivate";
    /** Sprint 3 (2026-11-24): STUDENT kullanıcı hesabı bağı (PUT bağla, DELETE kaldır). */
    public static final String ACCOUNT = "/{id}/account";
}
