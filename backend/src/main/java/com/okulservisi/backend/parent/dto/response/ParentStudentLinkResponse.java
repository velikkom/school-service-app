package com.okulservisi.backend.parent.dto.response;

import java.time.Instant;

public record ParentStudentLinkResponse(
        Long id,
        Long parentId,
        Long studentId,
        String relationType,
        boolean isActive,
        Instant revokedAt) {
}
