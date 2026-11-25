package com.okulservisi.backend.auth.entity;

/** {@code registration_request.status} — docs/DATABASE_DESIGN.md "Kullanıcı ve kimlik". */
public enum RegistrationRequestStatus {
    PENDING_EMAIL_VERIFICATION,
    EMAIL_VERIFIED,
    APPROVED,
    REJECTED
}
