package com.okulservisi.backend.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/notifications/broadcast} EMAIL kanalı — {@code PasswordResetMailService} ile AYNI
 * desen (bkz. {@code com.okulservisi.backend.auth.service.PasswordResetMailService}): SMTP (yerelde
 * Mailpit) üzerinden gerçek e-posta gönderir, ANCAK gönderim başarısız olursa (SMTP kapalı/erişilemez)
 * istisna YUTULUR ve sadece loglanır — broadcast işlemi bu yüzden ÇÖKMEZ, IN_APP bildirim kaydı her
 * durumda oluşturulmuş olur (bkz. {@link NotificationService#broadcast}).
 *
 * <p>{@code @Async} (Faz H takip turu, gerçek ölçümle bulunan performans sorunu): {@code
 * NotificationService#broadcast} alıcılar üzerinde döngüyle bu metodu çağırıyor; senkron SMTP
 * gönderimi (bağlantı başına ~1s) alıcı sayısıyla doğrusal olarak HTTP isteğini bloke ediyordu (39
 * alıcı → 42s, gerçek Mailpit'e karşı doğrulandı). `@EnableAsync` (`BackendApplication`) ile artık
 * her gönderim ayrı bir thread'de (varsayılan `SimpleAsyncTaskExecutor`) arka planda yapılıyor,
 * `broadcast` yanıtı DB yazımları tamamlanır tamamlanmaz (milisaniyeler içinde) döner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationMailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Async
    public void sendBroadcastEmail(String recipientEmail, String title, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject(title);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Bildirim e-postası gönderilemedi (alıcı maskelenmiştir).", ex);
        }
    }
}
