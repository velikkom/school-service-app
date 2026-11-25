package com.okulservisi.backend.driver.repository;
import com.okulservisi.backend.driver.entity.Driver;

import org.springframework.data.jpa.domain.Specification;

public final class DriverSpecifications {

    private DriverSpecifications() {
    }

    public static Specification<Driver> isActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? null : cb.equal(root.get("active"), isActive);
    }
}
