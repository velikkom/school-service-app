package com.okulservisi.backend.contract.repository;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.entity.ContractStatus;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class ContractSpecifications {

    private ContractSpecifications() {
    }

    public static Specification<Contract> hasStudentId(Long studentId) {
        return (root, query, cb) -> studentId == null ? null : cb.equal(root.get("student").get("id"), studentId);
    }

    public static Specification<Contract> hasStatus(ContractStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** PARENT kapsamı: sadece erişilebilir öğrenci id kümesindeki sözleşmeler (bkz. StudentScopePolicy). */
    public static Specification<Contract> studentIdIn(Collection<Long> studentIds) {
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
