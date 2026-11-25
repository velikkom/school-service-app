package com.okulservisi.backend.notification.service;

import com.okulservisi.backend.notification.repository.NotificationRepository;
import com.okulservisi.backend.user.entity.User;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2026-11-18 — operasyon olaylarının (sefer gecikmesi/saat değişikliği/iptal, personel ataması,
 * talep sonuçları) mevcut {@code notification} tablosu üzerinden IN_APP bildirimi.
 *
 * <p><b>Ne zaman oluşur:</b> çağıran domain servisinin transaction'ına KATILIR ({@link
 * Propagation#MANDATORY}) — iş kaydı commit edilmezse bildirim de oluşmaz (rollback ile birlikte
 * geri alınır), yani bildirim yalnızca işlem başarıyla kaydedildiğinde görünür.
 *
 * <p><b>Tekrar işlenme:</b> her olay deterministik bir {@code eventKey} taşır; aynı alıcıya aynı
 * anahtar ikinci kez yazılmaz ({@code ON CONFLICT DO NOTHING} + kısmi benzersiz indeks).
 */
@Service
@RequiredArgsConstructor
public class OperationalNotificationService {

    private static final int MAX_TITLE = 200;

    private final NotificationRepository notificationRepository;

    /**
     * @return gerçekten yeni yazılan bildirim sayısı.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int notify(Collection<User> recipients, String title, String body, String eventKey) {
        Map<Long, User> unique = new LinkedHashMap<>();
        for (User user : recipients) {
            if (user != null && user.isActive()) {
                unique.putIfAbsent(user.getId(), user);
            }
        }
        String safeTitle = title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title;
        int inserted = 0;
        for (User user : unique.values()) {
            inserted += notificationRepository.insertInAppIfAbsent(user.getId(), safeTitle, body, eventKey);
        }
        return inserted;
    }
}
