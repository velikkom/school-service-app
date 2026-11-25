package com.okulservisi.backend.vehicle.repository;
import com.okulservisi.backend.vehicle.entity.Vehicle;

import org.springframework.data.jpa.domain.Specification;

public final class VehicleSpecifications {

    private VehicleSpecifications() {
    }

    public static Specification<Vehicle> isActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? null : cb.equal(root.get("active"), isActive);
    }

    public static Specification<Vehicle> matchesQuery(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String pattern = "%" + q.trim().toLowerCase() + "%";
            // Sprint 3 düzeltmesi: plaka boşluksuz saklanır ("34ABC123"); kullanıcı arama kutusuna örnekteki gibi
            // "34 ABC 123" yazınca da bulunsun diye plaka araması boşluklar atılarak yapılır.
            String platePattern = "%" + q.replaceAll("\\s+", "").toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("plate")), platePattern),
                    cb.like(cb.lower(root.get("model")), pattern));
        };
    }
}
