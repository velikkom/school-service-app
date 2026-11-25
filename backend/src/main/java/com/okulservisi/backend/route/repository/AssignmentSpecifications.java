package com.okulservisi.backend.route.repository;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class AssignmentSpecifications {

    private AssignmentSpecifications() {
    }

    public static Specification<StudentRouteAssignment> hasStudentId(Long studentId) {
        return (root, query, cb) -> studentId == null ? null : cb.equal(root.get("student").get("id"), studentId);
    }

    public static Specification<StudentRouteAssignment> hasRouteId(Long routeId) {
        return (root, query, cb) -> routeId == null ? null : cb.equal(root.get("route").get("id"), routeId);
    }

    public static Specification<StudentRouteAssignment> hasDirection(RouteDirection direction) {
        return (root, query, cb) -> direction == null ? null : cb.equal(root.get("direction"), direction);
    }

    public static Specification<StudentRouteAssignment> studentIdIn(Collection<Long> studentIds) {
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
