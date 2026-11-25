package com.okulservisi.backend.notification.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.notification.dto.request.NotificationBroadcastRequest;
import com.okulservisi.backend.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * docs/API.md "15. Bildirim" (bkz. docs/RBAC_MATRIX.md: {@code /api/notifications/**} TÜM roller
 * 🟡 "kendine gelen"; ADMIN ayrıca broadcast yapabilir).
 */
public interface NotificationService {

    Page<Notification> listMine(AuthenticatedUser principal, Pageable pageable);

    Notification markRead(Long id, AuthenticatedUser principal);

    /**
     * {@code POST /api/notifications/broadcast} (SADECE ADMIN).
     *
     * @return benzersiz alıcı sayısı.
     */
    int broadcast(NotificationBroadcastRequest request);
}
