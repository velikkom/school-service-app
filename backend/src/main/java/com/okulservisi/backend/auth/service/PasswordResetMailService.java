package com.okulservisi.backend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Şifre sıfırlama e-postasını SMTP (yerelde Mailpit, host/port .env'den) üzerinden gönderir.
 * Ham reset token'ı burada sadece e-posta gövdesine yazılır; hiçbir log satırına yazılmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.reset-password-base-url:http://localhost:3000/sifre-sifirla}")
    private String resetPasswordBaseUrl;

    public void sendResetPasswordEmail(String recipientEmail, String rawToken) {
        String resetLink = resetPasswordBaseUrl + "?token=" + rawToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject("Şifre Sıfırlama Talebi");
        message.setText("""
                Şifre sıfırlama talebiniz alındı.

                Aşağıdaki bağlantıyı kullanarak şifrenizi sıfırlayabilirsiniz (30 dakika içinde geçerlidir):
                %s

                Bu talebi siz oluşturmadıysanız bu e-postayı göz ardı edebilirsiniz.
                """.formatted(resetLink));
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            // E-posta gönderimi başarısız olsa bile reset-password akışı (var/yok ayrımı yapmama) bozulmamalı;
            // hata detayları (token/alıcı içeriği) loglanmaz, sadece gönderim başarısızlığı not edilir.
            log.error("Şifre sıfırlama e-postası gönderilemedi (alıcı maskelenmiştir).", ex);
        }
    }
}
