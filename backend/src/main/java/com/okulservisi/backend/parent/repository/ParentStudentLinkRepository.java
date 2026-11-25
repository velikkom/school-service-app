package com.okulservisi.backend.parent.repository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, Long> {

    Optional<ParentStudentLink> findByParentIdAndStudentId(Long parentId, Long studentId);

    List<ParentStudentLink> findByParentIdAndActiveTrue(Long parentId);

    List<ParentStudentLink> findByStudentIdAndActiveTrue(Long studentId);

    boolean existsByParentIdAndStudentIdAndActiveTrue(Long parentId, Long studentId);

    List<ParentStudentLink> findByParentId(Long parentId);

    List<ParentStudentLink> findByStudentId(Long studentId);
}
