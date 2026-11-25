package com.okulservisi.backend.user.dto.request;

import com.okulservisi.backend.user.entity.Role;

/**
 * Alanların tümü opsiyoneldir (kısmi güncelleme — PATCH). {@code role}/{@code isActive} sadece bu
 * endpoint'ten (ADMIN) değiştirilebilir; docs/RBAC_MATRIX.md kural 3.
 */
public record UserUpdateRequest(String fullName, String phone, Role role, Boolean isActive) {
}
