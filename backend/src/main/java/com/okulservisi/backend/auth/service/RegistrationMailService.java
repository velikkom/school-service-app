package com.okulservisi.backend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Kayıt talebi e-posta doğrulama mesajını gönderir. {@link PasswordResetMailService} ile AYNI
 * desen: ham doğrulama token'ı burada sadece e-posta gövdesine yazılır, hiçbir log satırına
 * yazılmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationMailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.registration-verification-base-url:http://localhost:3000/e-posta-dogrula}")
    private String registrationVerificationBaseUrl;

    public void sendVerificationEmail(String recipientEmail, String rawToken) {
        String verificationLink = registrationVerificationBaseUrl + "?token=" + rawToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject("Kayıt Talebi - E-posta Doğrulama");
        message.setText("""
                Kayıt talebiniz alındı.

                Aşağıdaki bağlantıyı kullanarak e-posta adresinizi doğrulayabilirsiniz (24 saat içinde geçerlidir):
                %s

                Doğrulama tamamlandıktan sonra talebiniz ADMIN onayına gönderilecektir.
                Bu talebi siz oluşturmadıysanız bu e-postayı göz ardı edebilirsiniz.
                """.formatted(verificationLink));
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            // Kayıt talebi akışı (202 davranışı) e-posta gönderimi başarısız olsa bile bozulmamalı;
            // hata detayları (token/alıcı içeriği) loglanmaz, sadece gönderim başarısızlığı not edilir.
            log.error("Kayıt doğrulama e-postası gönderilemedi (alıcı maskelenmiştir).", ex);
        }
    }
}
