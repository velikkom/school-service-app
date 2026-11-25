package com.okulservisi.backend.auth.dto.response;

import com.okulservisi.backend.auth.entity.RegistrationRequestStatus;
import java.time.Instant;

/** docs/API.md §18 — {@code passwordHash} KESİNLİKLE YOK. */
public record RegistrationRequestResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        RegistrationRequestStatus status,
        Instant emailVerifiedAt,
        Instant approvedAt,
        Instant rejectedAt,
        String rejectionReason,
        Instant createdAt) {
}
