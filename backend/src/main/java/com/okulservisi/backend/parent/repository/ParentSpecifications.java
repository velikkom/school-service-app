package com.okulservisi.backend.parent.repository;
import com.okulservisi.backend.parent.entity.Parent;

import org.springframework.data.jpa.domain.Specification;

public final class ParentSpecifications {

    private ParentSpecifications() {
    }

    public static Specification<Parent> matchesQuery(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String pattern = "%" + q.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(root.get("phone"), pattern));
        };
    }
}
