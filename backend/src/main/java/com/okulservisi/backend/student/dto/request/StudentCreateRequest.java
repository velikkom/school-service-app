package com.okulservisi.backend.student.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * docs/API.md "3. Öğrenci": {@code POST /api/students} (ADMIN) — {@code fullName}, {@code birthDate},
 * {@code tcNo} (opsiyonel), {@code schoolId} (opsiyonel — §20'deki {@code school} listesinden seçilir,
 * {@code StudentService} seçilen okulun adını {@code schoolName}'e senkron yazar), {@code grade}.
 * {@code id}/{@code isActive}/{@code createdAt} gibi sistem alanları istemciden kabul edilmez.
 */
public record StudentCreateRequest(
        @NotBlank(message = "Ad soyad zorunludur.") String fullName,
        @NotNull(message = "Doğum tarihi zorunludur.") LocalDate birthDate,
        @Pattern(regexp = "\\d{11}", message = "T.C. kimlik numarası 11 hane olmalıdır.") String tcNo,
        Long schoolId,
        String grade) {
}
