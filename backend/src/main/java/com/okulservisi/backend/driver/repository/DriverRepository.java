package com.okulservisi.backend.driver.repository;
import com.okulservisi.backend.driver.entity.Driver;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DriverRepository extends JpaRepository<Driver, Long>, JpaSpecificationExecutor<Driver> {

    Optional<Driver> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** Mükerrer satır OLUŞMADIĞINI (reuse/eşzamanlılık testleri) kanıtlamak için — bkz. UserRoleChangeControllerTest. */
    long countByUserId(Long userId);
}
