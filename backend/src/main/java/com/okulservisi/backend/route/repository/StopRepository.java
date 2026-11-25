package com.okulservisi.backend.route.repository;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.Stop;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopRepository extends JpaRepository<Stop, Long> {

    List<Stop> findByRouteIdOrderBySequenceNoAsc(Long routeId);

    Optional<Stop> findByIdAndRouteId(Long id, Long routeId);

    boolean existsByRouteIdAndSequenceNo(Long routeId, Integer sequenceNo);

    /** 2026-11-18 (docs/API.md §25): bir yöndeki AKTİF güzergâhların durakları (veli durak seçim listesi). */
    @Query("select s from Stop s join fetch s.route r where r.active = true and r.direction = :direction "
            + "order by r.name asc, s.sequenceNo asc")
    List<Stop> findOnActiveRoutesByDirection(@Param("direction") RouteDirection direction);

    @Query("select s from Stop s join fetch s.route where s.id = :id")
    Optional<Stop> findWithRouteById(@Param("id") Long id);
}
