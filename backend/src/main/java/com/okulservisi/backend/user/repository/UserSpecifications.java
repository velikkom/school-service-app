package com.okulservisi.backend.user.repository;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;

import org.springframework.data.jpa.domain.Specification;

/** GET /api/users filtreleri (role, isActive, q) için basit {@link Specification} yardımcıları. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> role == null ? null : cb.equal(root.get("role"), role);
    }

    public static Specification<User> isActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? null : cb.equal(root.get("active"), isActive);
    }

    /**
     * Sprint 3: {@code studentLinked=false} → hiçbir öğrenci kaydına bağlı olmayan hesaplar;
     * {@code true} → bir öğrenci kaydına bağlı hesaplar. {@code null} → filtre yok.
     */
    public static Specification<User> studentLinked(Boolean linked) {
        return (root, query, cb) -> {
            if (linked == null) {
                return null;
            }
            var sub = query.subquery(Long.class);
            var student = sub.from(com.okulservisi.backend.student.entity.Student.class);
            sub.select(student.get("id")).where(cb.equal(student.get("user").get("id"), root.get("id")));
            return linked ? cb.exists(sub) : cb.not(cb.exists(sub));
        };
    }

    public static Specification<User> matchesQuery(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String pattern = "%" + q.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern));
        };
    }
}
