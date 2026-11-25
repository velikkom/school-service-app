package com.okulservisi.backend.route.repository;

import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StopChangeRequest;
import com.okulservisi.backend.route.entity.StopChangeRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** docs/API.md §25. */
public interface StopChangeRequestRepository extends JpaRepository<StopChangeRequest, Long>,
        JpaSpecificationExecutor<StopChangeRequest> {

    @Override
    @EntityGraph(attributePaths = {"student", "requestedBy", "requestedStop", "requestedStop.route", "decidedBy"})
    Page<StopChangeRequest> findAll(Specification<StopChangeRequest> spec, Pageable pageable);

    boolean existsByStudentIdAndDirectionAndStatus(Long studentId, RouteDirection direction,
            StopChangeRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StopChangeRequest r where r.id = :id")
    Optional<StopChangeRequest> findByIdForUpdate(@Param("id") Long id);
}
