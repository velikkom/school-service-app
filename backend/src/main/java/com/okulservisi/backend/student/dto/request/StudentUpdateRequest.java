package com.okulservisi.backend.student.dto.request;

import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** Kısmi güncelleme (PATCH, ADMIN). Tüm alanlar opsiyoneldir — {@code null} alan dokunulmadan
 * bırakılır (bu yüzden {@code schoolId} ile mevcut okul ataması bu endpoint'ten TEMİZLENEMEZ,
 * sadece değiştirilebilir; {@code isActive} de buradan değil {@code POST /api/students/{id}/
 * deactivate} üzerinden değiştirilir, bkz. docs/API.md). */
public record StudentUpdateRequest(
        String fullName,
        LocalDate birthDate,
        @Pattern(regexp = "\\d{11}", message = "T.C. kimlik numarası 11 hane olmalıdır.") String tcNo,
        Long schoolId,
        String grade) {
}
