package com.okulservisi.backend.school.dto.request;

import jakarta.validation.constraints.NotBlank;

/** docs/API.md §20: {@code name} zorunlu ve benzersizdir (büyük/küçük harf duyarsız). */
public record SchoolCreateRequest(
        @NotBlank(message = "Okul adı zorunludur.") String name,
        String address,
        String phone) {
}
