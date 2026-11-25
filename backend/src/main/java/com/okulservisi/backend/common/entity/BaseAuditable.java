package com.okulservisi.backend.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Tüm entity'lerde ortak olan {@code created_at}/{@code updated_at} audit alanları.
 * Gerçek değerler veritabanı tarafında (DEFAULT now() + trigger) üretilir; buradaki
 * {@code @CreatedDate}/{@code @LastModifiedDate} sadece JPA tarafında okunabilir olmaları içindir
 * (insertable/updatable = false — Hibernate bu alanlara asla yazmaz, çakışma oluşmaz).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
