package com.okulservisi.backend.trip.repository;

import com.okulservisi.backend.trip.entity.TripIncident;
import com.okulservisi.backend.trip.entity.TripIncidentStatus;
import com.okulservisi.backend.trip.entity.TripIncidentType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** docs/API.md §23. */
public interface TripIncidentRepository extends JpaRepository<TripIncident, Long> {

    @EntityGraph(attributePaths = {"reportedBy", "resolvedBy"})
    List<TripIncident> findByTripIdOrderByCreatedAtDescIdDesc(Long tripId);

    @EntityGraph(attributePaths = {"reportedBy", "resolvedBy"})
    Optional<TripIncident> findFirstByTripIdAndTypeAndStatus(Long tripId, TripIncidentType type,
            TripIncidentStatus status);

    /** Günlük servis kartı için son bilgi kaydı (iptal talepleri hariç). */
    Optional<TripIncident> findFirstByTripIdAndTypeNotOrderByCreatedAtDescIdDesc(Long tripId, TripIncidentType excluded);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TripIncident i where i.id = :id and i.trip.id = :tripId")
    Optional<TripIncident> findByIdAndTripIdForUpdate(@Param("id") Long id, @Param("tripId") Long tripId);
}
