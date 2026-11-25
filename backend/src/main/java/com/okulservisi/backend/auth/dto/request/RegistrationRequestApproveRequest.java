package com.okulservisi.backend.auth.dto.request;

import com.okulservisi.backend.user.entity.Role;
import jakarta.validation.constraints.NotNull;

/**
 * docs/API.md §18 — {@code role} zorunlu (kayıt talebinde hiç yoktu, ADMIN burada belirler).
 * {@code licenseNo}: SADECE {@code role=DRIVER} iken zorunludur (service katmanında kontrol edilir,
 * {@code driver.license_no} NOT NULL olduğu için) — diğer roller için yok sayılır.
 */
public record RegistrationRequestApproveRequest(
        @NotNull(message = "Rol zorunludur.") Role role,
        String phone,
        String licenseNo) {
}
