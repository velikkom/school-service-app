package com.okulservisi.backend.notification.dto.request;

import com.okulservisi.backend.user.entity.Role;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * docs/API.md "15. Bildirim" — {@code POST /api/notifications/broadcast} (SADECE ADMIN). Hedef
 * alanlarından ({@code targetRole}, {@code studentIds}, {@code parentIds}) EN AZ BİRİ dolu olmalıdır
 * (service katmanında doğrulanır, 400) — tasarım kararı: aynı istekte birden fazla hedef türü birlikte
 * verilirse hepsi BİRLEŞTİRİLİR (tekrarlanan alıcılar tekilleştirilir), reddedilmez.
 */
public record NotificationBroadcastRequest(
        @NotBlank(message = "title zorunludur.") String title,
        @NotBlank(message = "body zorunludur.") String body,
        Role targetRole,
        List<Long> studentIds,
        List<Long> parentIds) {
}
