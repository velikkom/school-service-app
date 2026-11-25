package com.okulservisi.backend.enrollment.dto.request;

import com.okulservisi.backend.student.entity.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * docs/API.md §19: {@code POST /api/parents/me/student-enrollments} (PARENT only) — {@code
 * { schoolId, relationType, parentPhone, student: { fullName, birthDate, tcNo, grade, gender,
 * healthNote? }, address: { city, district, neighborhood, addressLine }, contractAccepted }}.
 * {@code parentId} BİLEREK bu DTO'da YOKTUR — her zaman {@code @AuthenticationPrincipal}'dan çözülen
 * veliye bağlanır (bkz. {@code EnrollmentController}/{@code EnrollmentService}, {@code
 * ParentController.me()} ile AYNI desen). {@code parentPhone} zorunlu: kayıt talebi formunda (§18)
 * telefon toplanmadığı için veli hesabının {@code parent.phone} alanı genelde boştur — bu akış (yan
 * etki olarak, ayrı bir {@code PATCH /api/parents/me} endpoint'i AÇMADAN) {@code parent.phone}'u bu
 * değerle günceller (bkz. {@code EnrollmentService.create}). Format kısıtı backend'de şart değil
 * (projede var olan {@code phone} alanlarının hiçbirinde — Driver/Hostess/Parent/User — bir {@code
 * @Pattern} yok), sadece {@code @NotBlank}.
 *
 * <p>{@code schoolId} zorunlu (§20'deki {@code school} listesinden seçilir) — {@code
 * EnrollmentService.create()} içinde çözülür, yoksa/aktif değilse {@code 400} (bkz. {@code
 * BadRequestException}); BİLEREK {@code @NotNull} dışında ek doğrulama bu DTO'da YOKTUR (var
 * olma/aktiflik kontrolü FK çözümlemesi gerektirdiği için serviste yapılır, VehicleService/diğer
 * FK-çözen alanlarla AYNI desen).
 *
 * <p>{@code contractAccepted} BİLEREK {@code @AssertTrue} ile değil serviste elle kontrol edilir
 * ({@code != true -> 400}, docs/API.md §19) — hem {@code null} hem {@code false} aynı 400'e düşer ve
 * mesaj Türkçe/özel olabilir.
 */
public record EnrollmentCreateRequest(
        @NotNull(message = "schoolId zorunludur.") Long schoolId,
        @NotBlank(message = "relationType zorunludur.") String relationType,
        @NotBlank(message = "parentPhone zorunludur.") String parentPhone,
        @Valid @NotNull(message = "student zorunludur.") StudentPart student,
        @Valid @NotNull(message = "address zorunludur.") AddressPart address,
        Boolean contractAccepted) {

    /** {@code healthNote} docs/API.md'de {@code ?} ile işaretli — TEK opsiyonel alan. */
    public record StudentPart(
            @NotBlank(message = "fullName zorunludur.") String fullName,
            @NotNull(message = "birthDate zorunludur.") LocalDate birthDate,
            @NotBlank(message = "tcNo zorunludur.")
            @Pattern(regexp = "\\d{11}", message = "T.C. kimlik numarası 11 hane olmalıdır.") String tcNo,
            @NotBlank(message = "grade zorunludur.") String grade,
            @NotNull(message = "gender zorunludur.") Gender gender,
            String healthNote) {
    }

    public record AddressPart(
            @NotBlank(message = "city zorunludur.") String city,
            @NotBlank(message = "district zorunludur.") String district,
            @NotBlank(message = "neighborhood zorunludur.") String neighborhood,
            @NotBlank(message = "addressLine zorunludur.") String addressLine) {
    }
}
