package com.okulservisi.backend.attendance.repository;
import com.okulservisi.backend.attendance.entity.AttendanceEvent;

import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

public final class AttendanceEventSpecifications {

    private AttendanceEventSpecifications() {
    }

    public static Specification<AttendanceEvent> hasStudentId(Long studentId) {
        return (root, query, cb) -> studentId == null ? null : cb.equal(root.get("student").get("id"), studentId);
    }

    public static Specification<AttendanceEvent> recordedFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("recordedAt"), from);
    }

    public static Specification<AttendanceEvent> recordedTo(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("recordedAt"), to);
    }

    /** DRIVER kapsamı (docs/RBAC_MATRIX.md "kendi seferindeki kayıtlar"): kaydın seferi bu DRIVER'a atanmış olmalı. */
    public static Specification<AttendanceEvent> tripDriverUserIdEquals(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("trip").get("driver").get("user").get("id"), userId);
    }

    /** HOSTESS kapsamı: kaydın seferi bu HOSTESS'e atanmış olmalı ({@code hostess} NULL olabilir, LEFT JOIN). */
    public static Specification<AttendanceEvent> tripHostessUserIdEquals(Long userId) {
        return (root, query, cb) -> cb.equal(
                root.join("trip").join("hostess", JoinType.LEFT).get("user").get("id"), userId);
    }
}
