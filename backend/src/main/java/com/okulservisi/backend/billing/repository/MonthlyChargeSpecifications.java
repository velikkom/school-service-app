package com.okulservisi.backend.billing.repository;
import com.okulservisi.backend.billing.entity.MonthlyCharge;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class MonthlyChargeSpecifications {

    private MonthlyChargeSpecifications() {
    }

    public static Specification<MonthlyCharge> hasStudentId(Long studentId) {
        return (root, query, cb) -> studentId == null ? null : cb.equal(root.get("student").get("id"), studentId);
    }

    public static Specification<MonthlyCharge> hasPeriod(String period) {
        return (root, query, cb) -> period == null ? null : cb.equal(root.get("period"), period);
    }

    /** PARENT kapsamı: sadece erişilebilir öğrenci id kümesindeki tahakkuklar (bkz. StudentScopePolicy). */
    public static Specification<MonthlyCharge> studentIdIn(Collection<Long> studentIds) {
        return (root, query, cb) -> {
            if (studentIds == null) {
                return null;
            }
            if (studentIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("student").get("id").in(
                    studentIds instanceof List<Long> list ? list : List.copyOf(studentIds));
        };
    }
}
