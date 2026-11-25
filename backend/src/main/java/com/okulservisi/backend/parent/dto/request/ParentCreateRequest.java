package com.okulservisi.backend.parent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * docs/API.md "4. Veli": {@code POST /api/parents} (ADMIN). {@code userId}, önceden
 * {@code POST /api/users} (role=PARENT) ile oluşturulmuş kullanıcıyı 1-1 bağlar (docs/DATABASE_DESIGN.md).
 */
public record ParentCreateRequest(
        @NotNull(message = "userId zorunludur.") Long userId,
        @NotBlank(message = "Ad soyad zorunludur.") String fullName,
        String phone,
        @Pattern(regexp = "\\d{11}", message = "T.C. kimlik numarası 11 hane olmalıdır.") String tcNo) {
}
