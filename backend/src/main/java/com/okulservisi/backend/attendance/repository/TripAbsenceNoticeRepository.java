package com.okulservisi.backend.attendance.repository;

import com.okulservisi.backend.attendance.entity.AbsenceNoticeStatus;
import com.okulservisi.backend.attendance.entity.TripAbsenceNotice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** docs/API.md §22. */
public interface TripAbsenceNoticeRepository extends JpaRepository<TripAbsenceNotice, Long> {

    @EntityGraph(attributePaths = {"student", "parent"})
    Optional<TripAbsenceNotice> findByTripIdAndStudentIdAndStatus(Long tripId, Long studentId,
            AbsenceNoticeStatus status);

    @EntityGraph(attributePaths = {"student", "parent"})
    List<TripAbsenceNotice> findByTripIdAndStatusOrderByCreatedAtAsc(Long tripId, AbsenceNoticeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from TripAbsenceNotice n where n.id = :id and n.trip.id = :tripId")
    Optional<TripAbsenceNotice> findByIdAndTripIdForUpdate(@Param("id") Long id, @Param("tripId") Long tripId);
}
