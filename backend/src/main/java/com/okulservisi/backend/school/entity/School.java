package com.okulservisi.backend.school.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code school} tablosu. Taşıma firmasının servis verdiği okulların ADMIN tarafından yönetilen
 * basit bir referans listesi (bkz. docs/API.md §20) — multi-tenant/firma DEĞİL. {@code name}
 * büyük/küçük harf duyarsız benzersizdir. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "school")
public class School extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
