package com.okulservisi.backend.attendance.repository;

import com.okulservisi.backend.attendance.entity.AttendanceCorrectionRequest;
import com.okulservisi.backend.attendance.entity.CorrectionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
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

/** docs/API.md §24. */
public interface AttendanceCorrectionRequestRepository extends JpaRepository<AttendanceCorrectionRequest, Long>,
        JpaSpecificationExecutor<AttendanceCorrectionRequest> {

    @Override
    @EntityGraph(attributePaths = {"trip", "student", "requestedBy", "decidedBy"})
    Page<AttendanceCorrectionRequest> findAll(Specification<AttendanceCorrectionRequest> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"trip", "student", "requestedBy", "decidedBy"})
    Optional<AttendanceCorrectionRequest> findById(Long id);

    List<AttendanceCorrectionRequest> findByTripIdAndStatus(Long tripId, CorrectionStatus status);

    boolean existsByTripIdAndStudentIdAndStatus(Long tripId, Long studentId, CorrectionStatus status);

    /** Onay/ret: aynı talebin eşzamanlı iki kez sonuçlandırılmasını satır kilidiyle serileştirir. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AttendanceCorrectionRequest r where r.id = :id")
    Optional<AttendanceCorrectionRequest> findByIdForUpdate(@Param("id") Long id);
}
