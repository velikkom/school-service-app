package com.okulservisi.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** {@code created_at}/{@code updated_at} alanlarının {@code BaseAuditable} üzerinden okunabilmesi için. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
