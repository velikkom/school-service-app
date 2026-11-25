package com.okulservisi.backend.parent.service;

import com.okulservisi.backend.parent.dto.request.ParentStudentLinkCreateRequest;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import java.util.List;

/**
 * docs/API.md "4. Veli": {@code POST /api/parent-student-links} aynı çift için tekrar edemez
 * (DB UNIQUE(parent_id, student_id) — docs/DATABASE_DESIGN.md), {@code /revoke} ile soft pasife alınır
 * (silme yok, geçmiş erişim kaydı kalır).
 */
public interface ParentStudentLinkService {

    ParentStudentLink getById(Long id);

    List<ParentStudentLink> list(Long studentId, Long parentId);

    ParentStudentLink create(ParentStudentLinkCreateRequest request);

    ParentStudentLink revoke(Long id);
}
