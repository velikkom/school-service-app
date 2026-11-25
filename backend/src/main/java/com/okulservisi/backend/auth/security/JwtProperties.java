package com.okulservisi.backend.auth.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT ayarları. docs/SECURITY.md kuralı: {@code JWT_SECRET} 32 byte'tan kısaysa uygulama
 * başlarken fail-fast hata verir. Bu kontrol bean oluşturulurken ({@code @PostConstruct}) yapılır,
 * yani ApplicationContext tamamlanmadan (ilk bean refresh aşamasında) başarısız olur ve uygulama
 * hiçbir isteği kabul etmeden kapanır.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private static final int MIN_SECRET_BYTES = 32;

    /** Base64 veya düz metin olabilir; en az 32 byte (256 bit) olmalıdır. */
    private String secret;
    private long accessTokenTtlMinutes = 15;
    private long refreshTokenTtlDays = 7;
    /** "Beni hatırla" işaretlendiğinde kullanılan, daha uzun refresh token TTL'i (bkz. docs/SECURITY.md). */
    private long refreshTokenTtlDaysRememberMe = 30;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET ortam değişkeni tanımlı değil. Uygulama güvenlik nedeniyle başlatılamıyor.");
        }
        byte[] rawBytes = resolveKeyBytes();
        if (rawBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET en az " + MIN_SECRET_BYTES + " byte (256 bit) olmalıdır, mevcut: "
                            + rawBytes.length + " byte. Uygulama güvenlik nedeniyle başlatılamıyor.");
        }
    }

    /** Secret'i Base64 olarak decode etmeyi dener; başarısız olursa ham UTF-8 byte'ları kullanır. */
    public byte[] resolveKeyBytes() {
        try {
            return java.util.Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            return secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
