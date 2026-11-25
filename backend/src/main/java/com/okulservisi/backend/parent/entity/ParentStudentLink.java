package com.okulservisi.backend.parent.entity;

import com.okulservisi.backend.common.entity.BaseAuditable;
import com.okulservisi.backend.student.entity.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code parent_student_link} — veli/öğrenci ilişkisi. Silme yok; {@code revoke} ile pasife alınır. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "parent_student_link")
public class ParentStudentLink extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "relation_type", nullable = false)
    private String relationType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
