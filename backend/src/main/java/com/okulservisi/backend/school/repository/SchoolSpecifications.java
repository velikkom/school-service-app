package com.okulservisi.backend.school.repository;

import com.okulservisi.backend.school.entity.School;
import org.springframework.data.jpa.domain.Specification;

public final class SchoolSpecifications {

    private SchoolSpecifications() {
    }

    public static Specification<School> isActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? null : cb.equal(root.get("active"), isActive);
    }

    public static Specification<School> matchesQuery(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String pattern = "%" + q.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("address")), pattern));
        };
    }
}
