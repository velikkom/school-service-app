package com.okulservisi.backend.school.dto.request;

/** Kısmi güncelleme (PATCH, ADMIN). {@code isActive} de bu endpoint üzerinden (ADMIN) değiştirilir —
 * docs/API.md bu modül için ayrı bir deactivate alt-endpoint'i tanımlamamıştır (Vehicle ile aynı). */
public record SchoolUpdateRequest(
        String name,
        String address,
        String phone,
        Boolean isActive) {
}
