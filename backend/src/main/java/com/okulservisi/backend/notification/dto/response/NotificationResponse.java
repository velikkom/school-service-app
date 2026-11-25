package com.okulservisi.backend.notification.dto.response;

import com.okulservisi.backend.notification.entity.NotificationChannel;
import java.time.Instant;

/** docs/API.md "15. Bildirim" — {@code GET /api/notifications} liste öğesi. */
public record NotificationResponse(
        Long id,
        Long recipientUserId,
        NotificationChannel channel,
        String title,
        String body,
        boolean read,
        Instant createdAt) {
}
