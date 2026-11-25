package com.okulservisi.backend.vehicle.entity;

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

/** {@code vehicle} tablosu. Plaka normalize edilmiş (büyük harf, boşluksuz) halde benzersizdir. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vehicle")
public class Vehicle extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plate", nullable = false)
    private String plate;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "model")
    private String model;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
