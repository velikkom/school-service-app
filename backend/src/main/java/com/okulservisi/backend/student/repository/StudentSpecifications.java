package com.okulservisi.backend.student.repository;
import com.okulservisi.backend.student.entity.Student;

import jakarta.persistence.criteria.Subquery;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** {@code GET /api/students} filtreleri (q, routeId, isActive) için {@link Specification} yardımcıları. */
public final class StudentSpecifications {

    private StudentSpecifications() {
    }

    public static Specification<Student> isActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? null : cb.equal(root.get("active"), isActive);
    }

    public static Specification<Student> matchesQuery(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String pattern = "%" + q.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("schoolName")), pattern));
        };
    }

    /** Öğrencinin verilen route'a (herhangi bir dönemde) atanmış olup olmadığını kontrol eder. */
    public static Specification<Student> assignedToRoute(Long routeId) {
        return (root, query, cb) -> {
            if (routeId == null) {
                return null;
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            var assignmentRoot = subquery.from(com.okulservisi.backend.route.entity.StudentRouteAssignment.class);
            subquery.select(assignmentRoot.get("id"))
                    .where(cb.equal(assignmentRoot.get("student").get("id"), root.get("id")),
                            cb.equal(assignmentRoot.get("route").get("id"), routeId));
            return cb.exists(subquery);
        };
    }

    public static Specification<Student> idIn(Collection<Long> ids) {
        return (root, query, cb) -> {
            if (ids == null) {
                return null;
            }
            if (ids.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("id").in(ids instanceof List<Long> list ? list : List.copyOf(ids));
        };
    }
}
