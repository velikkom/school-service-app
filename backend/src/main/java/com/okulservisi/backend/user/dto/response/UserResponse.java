package com.okulservisi.backend.user.dto.response;

import com.okulservisi.backend.user.entity.Role;

/**
 * docs/API.md: "Request/response DTO'ları passwordHash, createdAt, createdBy gibi sistem
 * alanlarını içermez/kabul etmez" — bu yüzden {@code createdAt} kasıtlı olarak yoktur.
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role,
        boolean isActive) {
}
