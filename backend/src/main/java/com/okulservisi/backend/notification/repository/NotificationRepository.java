package com.okulservisi.backend.notification.repository;
import com.okulservisi.backend.notification.entity.Notification;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    /**
     * EKLENDİ (Faz H): {@code POST /api/notifications/{id}/read} nesne bazlı erişim kontrolü — bir
     * kullanıcı SADECE kendi {@code recipient_user_id} ile eşleşen bildirimi bulabilir (başkasının
     * bildirimi bu sorguyla boş döner, çağıran taraf bunu 404'e çevirir — bkz. StudentAccessGuard ile
     * aynı "var/yok bilgisi sızdırılmaz" konvansiyonu).
     */
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    /**
     * 2026-11-18 — operasyon bildirimi: aynı (alıcı, kanal, event_key) zaten varsa HİÇBİR şey yazmaz
     * ({@code uq_notification_recipient_channel_event} kısmi benzersiz indeksi + {@code ON CONFLICT DO
     * NOTHING}). Eşzamanlı/tekrar işlenen olaylarda da tek kayıt kalır.
     *
     * @return eklenen satır sayısı (0 = zaten vardı).
     */
    @Modifying
    @Query(value = "INSERT INTO notification (recipient_user_id, channel, title, body, is_read, event_key) "
            + "VALUES (:recipientId, 'IN_APP', :title, :body, FALSE, :eventKey) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertInAppIfAbsent(@Param("recipientId") Long recipientId, @Param("title") String title,
            @Param("body") String body, @Param("eventKey") String eventKey);

    long countByRecipientIdAndEventKey(Long recipientId, String eventKey);
}
