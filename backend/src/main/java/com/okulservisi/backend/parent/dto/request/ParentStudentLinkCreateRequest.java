package com.okulservisi.backend.parent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** docs/API.md "4. Veli": {@code POST /api/parent-student-links} — aynı çift tekrar edemez (409). */
public record ParentStudentLinkCreateRequest(
        @NotNull(message = "parentId zorunludur.") Long parentId,
        @NotNull(message = "studentId zorunludur.") Long studentId,
        @NotBlank(message = "relationType zorunludur.") String relationType) {
}
