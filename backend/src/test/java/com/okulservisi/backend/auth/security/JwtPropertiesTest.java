package com.okulservisi.backend.auth.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * docs/SECURITY.md: "JWT secret... yoksa veya 32 byte'tan kısaysa başlamayı reddeder (fail-fast)".
 * docs/TEST_PLAN.md eşleştirmesi: T-20.
 *
 * <p>Bu bir birim testidir (Spring context/Testcontainers gerektirmez) - {@code JwtProperties}
 * sınıfının {@code @PostConstruct validate()} mantığını doğrudan çağırarak doğrular. Gerçek
 * uygulama context'inin eksik/kısa secret ile başlamayı reddettiğini uçtan uca doğrulamak için
 * ayrıca bkz. not aşağıda (ApplicationContext tabanlı tam doğrulama bu turda eklenmedi - bu birim
 * testi production kuralıyla aynı kod yolunu (validate()) tetiklediği için yeterli kabul edildi).
 */
class JwtPropertiesTest {

    @Test
    void validate_whenSecretIsNull_throwsFailFast() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(null);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void validate_whenSecretIsBlank_throwsFailFast() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("   ");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void validate_whenSecretShorterThan32Bytes_throwsFailFast() {
        JwtProperties properties = new JwtProperties();
        // 16 byte ham UTF-8 (base64 olarak decode edilemeyecek şekilde kasıtlı - "!" base64 değil).
        properties.setSecret("too-short-secret!");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }

    @Test
    void validate_whenBase64Secret_decodesToFewerThan32Bytes_throwsFailFast() {
        JwtProperties properties = new JwtProperties();
        // Geçerli base64 ama decode edilince 16 byte -> reddedilmeli.
        properties.setSecret(Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }

    @Test
    void validate_whenSecretIsAtLeast32Bytes_doesNotThrow() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(new byte[32]));

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
