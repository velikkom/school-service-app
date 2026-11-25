package com.okulservisi.backend.student.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import com.okulservisi.backend.school.entity.School;
import com.okulservisi.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code student} tablosu. {@code user} alanı, öğrencinin kendi hesabıyla (STUDENT rolü) girişi varsa bağlanır. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "student")
public class Student extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "tc_no")
    private String tcNo;

    @Column(name = "school_name")
    private String schoolName;

    /** 2026-10-25, veli öz-kayıt sihirbazı (bkz. docs/API.md §19/§20) — nullable, ADMIN'in doğrudan
     * oluşturduğu öğrencilerde {@code null} kalabilir (mevcut {@code schoolName} serbest metin alanı
     * geriye dönük uyum için DOKUNULMADAN kalır, ikisi birlikte var olur). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "grade")
    private String grade;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** 2026-10-25, veli öz-kayıt sihirbazı (bkz. docs/API.md §19) — nullable, ADMIN'in doğrudan
     * oluşturduğu öğrencilerde boş bırakılabilir. */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "health_note")
    private String healthNote;

    @Column(name = "city")
    private String city;

    @Column(name = "district")
    private String district;

    @Column(name = "neighborhood")
    private String neighborhood;

    @Column(name = "address_line")
    private String addressLine;
}
