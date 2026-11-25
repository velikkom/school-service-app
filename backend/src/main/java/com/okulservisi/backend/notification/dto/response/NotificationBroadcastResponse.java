package com.okulservisi.backend.notification.dto.response;

/** {@code POST /api/notifications/broadcast} yanıtı — kaç BENZERSİZ alıcıya ulaşıldığı (her biri için IN_APP+EMAIL çifti oluşturulur). */
public record NotificationBroadcastResponse(int recipientCount) {
}
