package com.okulservisi.backend.trip.repository;

import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.entity.TripStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public final class TripSpecifications {

    private TripSpecifications() {
    }

    public static Specification<Trip> hasId(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    public static Specification<Trip> hasRouteId(Long routeId) {
        return (root, query, cb) -> routeId == null ? null : cb.equal(root.get("route").get("id"), routeId);
    }

    public static Specification<Trip> hasVehicleId(Long vehicleId) {
        return (root, query, cb) -> vehicleId == null ? null : cb.equal(root.get("vehicle").get("id"), vehicleId);
    }

    public static Specification<Trip> hasDriverId(Long driverId) {
        return (root, query, cb) -> driverId == null ? null : cb.equal(root.get("driver").get("id"), driverId);
    }

    public static Specification<Trip> hasHostessId(Long hostessId) {
        return (root, query, cb) -> hostessId == null ? null
                : cb.equal(root.join("hostess", JoinType.LEFT).get("id"), hostessId);
    }

    public static Specification<Trip> hasBusinessDate(LocalDate businessDate) {
        return (root, query, cb) -> businessDate == null ? null : cb.equal(root.get("businessDate"), businessDate);
    }

    public static Specification<Trip> hasDirection(RouteDirection direction) {
        return (root, query, cb) -> direction == null ? null : cb.equal(root.get("direction"), direction);
    }

    public static Specification<Trip> hasStatus(TripStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** DRIVER kapsamı: sadece {@code driver.user.id == userId} olan seferler. */
    public static Specification<Trip> assignedToDriverUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("driver").get("user").get("id"), userId);
    }

    /** HOSTESS kapsamı: sadece {@code hostess.user.id == userId} olan seferler ({@code hostess} NULL olabilir, LEFT JOIN). */
    public static Specification<Trip> assignedToHostessUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.join("hostess", JoinType.LEFT).get("user").get("id"), userId);
    }

    /**
     * PARENT/STUDENT kapsamı: verilen öğrenci id kümesinden en az birinin, bu seferin route+direction'ına
     * eşleşen VE businessDate'i kapsayan (valid_from &lt;= businessDate &lt;= valid_to veya valid_to NULL)
     * aktif bir {@code StudentRouteAssignment} kaydı olup olmadığını korelasyonlu bir {@code EXISTS}
     * alt sorgusuyla kontrol eder (bkz. docs/DATABASE_DESIGN.md "Sefer / Yoklama").
     */
    public static Specification<Trip> accessibleToStudents(Set<Long> studentIds) {
        return (root, query, cb) -> {
            if (studentIds == null || studentIds.isEmpty()) {
                return cb.disjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<StudentRouteAssignment> sra = subquery.from(StudentRouteAssignment.class);
            subquery.select(sra.get("id"));
            subquery.where(cb.and(
                    sra.get("student").get("id").in(studentIds),
                    cb.equal(sra.get("route").get("id"), root.get("route").get("id")),
                    cb.equal(sra.get("direction"), root.get("direction")),
                    cb.lessThanOrEqualTo(sra.get("validFrom"), root.get("businessDate")),
                    cb.or(cb.isNull(sra.get("validTo")), cb.greaterThanOrEqualTo(sra.get("validTo"), root.get("businessDate")))));
            return cb.exists(subquery);
        };
    }

    public static Specification<Trip> none() {
        return (root, query, cb) -> cb.disjunction();
    }
}
