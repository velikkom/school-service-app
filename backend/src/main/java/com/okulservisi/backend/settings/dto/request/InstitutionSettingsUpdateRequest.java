package com.okulservisi.backend.settings.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * docs/API.md "17. Kurum ayarları" — {@code PATCH /api/settings} {@code {name?, address?, phone?,
 * reportHeaderNote?, defaultMonthlyFee?}}. PATCH yarı-kısmi güncelleme semantiğindedir: {@code null}
 * bırakılan alan DEĞİŞTİRİLMEZ (mevcut değer korunur); bir alanı BOŞALTMAK isteniyorsa boş string
 * ({@code ""}) gönderilir (NULL ile "belirtilmedi" arasındaki fark budur) — {@code defaultMonthlyFee}
 * sayısal bir alan olduğu için bu "boşaltma" mekanizmasına sahip değildir (docs/API.md'de de ayrıca
 * tanımlanmamıştır), sadece {@code null}=değişmez semantiği geçerlidir.
 */
public record InstitutionSettingsUpdateRequest(
        @Size(min = 1, max = 200, message = "name 1-200 karakter olmalıdır.") String name,
        @Size(max = 255, message = "address en fazla 255 karakter olmalıdır.") String address,
        @Size(max = 20, message = "phone en fazla 20 karakter olmalıdır.") String phone,
        @Size(max = 255, message = "reportHeaderNote en fazla 255 karakter olmalıdır.") String reportHeaderNote,
        @Positive(message = "defaultMonthlyFee pozitif olmalıdır.") BigDecimal defaultMonthlyFee) {
}
