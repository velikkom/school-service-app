package com.okulservisi.backend.route.repository;

import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.trip.entity.Trip;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public final class RouteSpecifications {

    private RouteSpecifications() {
    }

    public static Specification<Route> isActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? null : cb.equal(root.get("active"), isActive);
    }

    public static Specification<Route> hasDirection(RouteDirection direction) {
        return (root, query, cb) -> direction == null ? null : cb.equal(root.get("direction"), direction);
    }

    public static Specification<Route> matchesQuery(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.get("name")), "%" + q.trim().toLowerCase() + "%");
        };
    }

    public static Specification<Route> hasId(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    /**
     * DRIVER kapsamı (docs/RBAC_MATRIX.md "kendi atanmış güzergâh, salt okunur"): bu route'un, bu
     * DRIVER'a atanmış en az bir {@code Trip}'i olup olmadığını korelasyonlu bir {@code EXISTS} alt
     * sorgusuyla kontrol eder (bkz. {@code TripSpecifications.accessibleToStudents} ile aynı desen).
     */
    public static Specification<Route> assignedToDriverUser(Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Trip> trip = subquery.from(Trip.class);
            subquery.select(trip.get("id"));
            subquery.where(cb.and(
                    cb.equal(trip.get("route").get("id"), root.get("id")),
                    cb.equal(trip.get("driver").get("user").get("id"), userId)));
            return cb.exists(subquery);
        };
    }

    /** HOSTESS kapsamı — {@link #assignedToDriverUser} ile aynı desen, {@code hostess} NULL olabilir. */
    public static Specification<Route> assignedToHostessUser(Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Trip> trip = subquery.from(Trip.class);
            subquery.select(trip.get("id"));
            subquery.where(cb.and(
                    cb.equal(trip.get("route").get("id"), root.get("id")),
                    cb.equal(trip.get("hostess").get("user").get("id"), userId)));
            return cb.exists(subquery);
        };
    }

    public static Specification<Route> none() {
        return (root, query, cb) -> cb.disjunction();
    }
}
