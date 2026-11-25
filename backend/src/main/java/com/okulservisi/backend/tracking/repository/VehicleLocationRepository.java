package com.okulservisi.backend.tracking.repository;
import com.okulservisi.backend.tracking.entity.VehicleLocation;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleLocationRepository extends JpaRepository<VehicleLocation, Long> {

    Optional<VehicleLocation> findFirstByTripIdOrderByRecordedAtDesc(Long tripId);

    /** docs/DATABASE_DESIGN.md "vehicle_location ... Saklama: 90 gün" — silinen kayıt sayısını döner. */
    long deleteByRecordedAtBefore(Instant cutoff);
}
