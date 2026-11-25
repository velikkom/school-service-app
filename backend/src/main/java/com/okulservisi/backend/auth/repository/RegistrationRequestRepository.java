package com.okulservisi.backend.auth.repository;
import com.okulservisi.backend.auth.entity.RegistrationRequest;
import com.okulservisi.backend.auth.entity.RegistrationRequestStatus;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {

    @Query("select r from RegistrationRequest r where lower(r.email) = lower(:email) and r.status in :statuses "
            + "order by r.id desc")
    List<RegistrationRequest> findByEmailIgnoreCaseAndStatusIn(@Param("email") String email,
            @Param("statuses") List<RegistrationRequestStatus> statuses);

    Page<RegistrationRequest> findByStatus(RegistrationRequestStatus status, Pageable pageable);
}
