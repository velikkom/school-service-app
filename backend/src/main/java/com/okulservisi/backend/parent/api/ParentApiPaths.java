package com.okulservisi.backend.parent.api;

/**
 * docs/API.md "4. Veli" — parent domain'indeki iki controller'ın ({@code ParentController},
 * {@code ParentStudentLinkController}) mapping sabitleri.
 */
public final class ParentApiPaths {

    private ParentApiPaths() {
    }

    // --- ParentController: /api/parents ---
    public static final String PARENT_BASE = "/api/parents";
    public static final String ME = "/me";
    public static final String BY_ID = "/{id}";

    // --- ParentStudentLinkController: /api/parent-student-links ---
    public static final String PARENT_STUDENT_LINK_BASE = "/api/parent-student-links";
    public static final String REVOKE = "/{id}/revoke";
}
