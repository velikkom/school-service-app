package com.okulservisi.backend.settings.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code institution_settings} — tek satırlık kurum ayarları (multi-tenant DEĞİL, bkz. CLAUDE.md). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "institution_settings")
public class InstitutionSettings extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "report_header_note")
    private String reportHeaderNote;

    /** 2026-10-25, veli öz-kayıt sihirbazı (bkz. docs/API.md §17/§19) — nullable; {@code null} iken
     * self-enrollment akışı 409 ile reddedilir ("kurum henüz varsayılan ücret belirlememiş"). */
    @Column(name = "default_monthly_fee", precision = 12, scale = 2)
    private BigDecimal defaultMonthlyFee;
}
