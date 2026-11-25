package com.okulservisi.backend.user.dto.request;

import com.okulservisi.backend.user.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * docs/API.md "2. Kullanıcı yönetimi" — {@code POST /api/users/{id}/role}. Rol değişikliğini ve
 * (DRIVER/HOSTESS/PARENT hedefliyorsa) gerekli domain profilinin oluşturulmasını/yeniden
 * kullanılmasını TEK bir veritabanı transaction'ında birleştirir (bkz. {@code UserService#changeRole}).
 * {@code licenseNo}: SADECE {@code role=DRIVER} iken VE kullanıcının henüz bir Driver profili YOKSA
 * zorunludur (driver.license_no NOT NULL) — mevcut bir profil varsa yok sayılır/opsiyoneldir.
 */
public record UserRoleChangeRequest(
        @NotNull(message = "Rol zorunludur.") Role role,
        String licenseNo) {
}
