package com.okulservisi.backend.hostess.repository;
import com.okulservisi.backend.hostess.entity.Hostess;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HostessRepository extends JpaRepository<Hostess, Long>, JpaSpecificationExecutor<Hostess> {

    Optional<Hostess> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
