package com.okulservisi.backend.student.dto.response;

import com.okulservisi.backend.student.entity.Gender;
import java.time.LocalDate;

/** {@code passwordHash}/{@code createdAt} gibi sistem alanları yok (docs/API.md).
 *
 * <p>{@code gender}/{@code healthNote}/{@code city}/{@code district}/{@code neighborhood}/
 * {@code addressLine}: 2026-10-25'te veli öz-kayıt sihirbazı için eklendi (bkz. docs/API.md §19) —
 * ADMIN'in doğrudan oluşturduğu öğrencilerde {@code null} olabilir.
 *
 * <p>{@code schoolId}: 2026-10-25'te §20 ile eklendi — nullable (sadece veli öz-kayıt sihirbazından
 * oluşturulan öğrencilerde set edilir, bkz. docs/API.md §3/§19/§20); {@code schoolName} geriye dönük
 * uyum için KORUNUR, ikisi senkron tutulur. */
public record StudentResponse(
        Long id,
        Long userId,
        String fullName,
        LocalDate birthDate,
        String tcNo,
        String schoolName,
        Long schoolId,
        String grade,
        boolean isActive,
        Gender gender,
        String healthNote,
        String city,
        String district,
        String neighborhood,
        String addressLine) {
}
