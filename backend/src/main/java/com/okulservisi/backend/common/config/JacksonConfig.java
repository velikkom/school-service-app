package com.okulservisi.backend.common.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * docs/API.md kuralı: "Para JSON'da string olarak taşınır (örn. "1250.00")". Uygulamadaki tüm
 * {@link BigDecimal} alanları para tutarıdır (precision=12, scale=2), bu yüzden global olarak
 * string + 2 ondalık basamak formatında serileştirilir.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalAsStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(BigDecimal.class,
                    new com.fasterxml.jackson.databind.JsonSerializer<BigDecimal>() {
                        @Override
                        public void serialize(BigDecimal value, com.fasterxml.jackson.core.JsonGenerator gen,
                                com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                            gen.writeString(value.setScale(2, RoundingMode.HALF_UP).toPlainString());
                        }
                    });
            builder.modulesToInstall(module);
        };
    }
}
