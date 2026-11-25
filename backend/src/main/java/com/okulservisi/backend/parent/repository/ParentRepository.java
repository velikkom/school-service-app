package com.okulservisi.backend.parent.repository;
import com.okulservisi.backend.parent.entity.Parent;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ParentRepository extends JpaRepository<Parent, Long>, JpaSpecificationExecutor<Parent> {

    Optional<Parent> findByUserId(Long userId);

    /** 2026-11-18: veli öz-kayıt başvurularını veli bazında serileştirir ({@code SELECT ... FOR UPDATE}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parent p where p.id = :id")
    Optional<Parent> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUserId(Long userId);
}
