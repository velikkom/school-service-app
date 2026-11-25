package com.okulservisi.backend.school.repository;

import com.okulservisi.backend.school.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SchoolRepository extends JpaRepository<School, Long>, JpaSpecificationExecutor<School> {

    boolean existsByNameIgnoreCase(String name);
}
