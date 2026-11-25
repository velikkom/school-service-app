package com.okulservisi.backend.hostess.repository;
import com.okulservisi.backend.hostess.entity.Hostess;

import org.springframework.data.jpa.domain.Specification;

public final class HostessSpecifications {

    private HostessSpecifications() {
    }

    public static Specification<Hostess> isActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? null : cb.equal(root.get("active"), isActive);
    }
}
