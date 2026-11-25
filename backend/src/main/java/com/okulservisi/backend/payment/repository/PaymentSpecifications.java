package com.okulservisi.backend.payment.repository;
import com.okulservisi.backend.payment.entity.Payment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    public static Specification<Payment> hasStudentId(Long studentId) {
        return (root, query, cb) -> studentId == null ? null : cb.equal(root.get("student").get("id"), studentId);
    }

    /** PARENT kapsamı: sadece erişilebilir öğrenci id kümesindeki ödemeler (bkz. StudentScopePolicy). */
    public static Specification<Payment> studentIdIn(Collection<Long> studentIds) {
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
