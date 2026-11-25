package com.okulservisi.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} (Faz G): {@code BillingScheduler}'ın Europe/Istanbul, ayın 1'i 03:00
 * cron job'ı için (docs/API.md "12. Aylık ücret / tahakkuk") gereklidir.
 *
 * <p>{@code @EnableAsync} (Faz H takip turu): {@code NotificationMailService#sendBroadcastEmail}'in
 * {@code @Async} çalışması için — ölçüm: 39 alıcılı bir broadcast, senkron SMTP gönderiminde (her
 * bağlantı ~1s) 42 saniye sürüyordu (gerçek Mailpit'e karşı doğrulandı, port yanlış yapılandırması
 * DEĞİLDİ) ve HTTP isteğini bloke ediyordu — tek işlemci/thread'li varsayılan {@code
 * SimpleAsyncTaskExecutor} ile (özel bir havuz tanımlanmadı, bu düşük trafikli bir ADMIN eylemi,
 * ekstra karmaşıklık gerektirmiyor) e-posta gönderimi artık arka planda, HTTP yanıtını bloklamadan
 * yapılıyor.
 */
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
