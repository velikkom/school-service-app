package com.okulservisi.backend.parent.service.impl;

import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.parent.dto.request.ParentStudentLinkCreateRequest;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.parent.service.ParentStudentLinkService;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "4. Veli": {@code POST /api/parent-student-links} aynı çift için tekrar edemez
 * (DB UNIQUE(parent_id, student_id) — docs/DATABASE_DESIGN.md), {@code /revoke} ile soft pasife alınır
 * (silme yok, geçmiş erişim kaydı kalır).
 */
@Service
@RequiredArgsConstructor
public class ParentStudentLinkServiceImpl implements ParentStudentLinkService {

    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;

    @Override
    public ParentStudentLink getById(Long id) {
        return parentStudentLinkRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Veli-öğrenci bağlantısı", id));
    }

    @Override
    public List<ParentStudentLink> list(Long studentId, Long parentId) {
        if (studentId == null && parentId == null) {
            throw new BadRequestException("studentId veya parentId'den en az biri verilmelidir.");
        }
        if (studentId != null && parentId != null) {
            return parentStudentLinkRepository.findByParentIdAndStudentId(parentId, studentId)
                    .map(List::of)
                    .orElseGet(List::of);
        }
        if (parentId != null) {
            return parentStudentLinkRepository.findByParentId(parentId);
        }
        return parentStudentLinkRepository.findByStudentId(studentId);
    }

    @Override
    @Transactional
    public ParentStudentLink create(ParentStudentLinkCreateRequest request) {
        Parent parent = parentRepository.findById(request.parentId())
                .orElseThrow(() -> new BadRequestException("Belirtilen parentId'ye sahip veli bulunamadı."));
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new BadRequestException("Belirtilen studentId'ye sahip öğrenci bulunamadı."));

        if (parentStudentLinkRepository.findByParentIdAndStudentId(parent.getId(), student.getId()).isPresent()) {
            throw new ConflictException("Bu veli-öğrenci bağlantısı zaten var.");
        }

        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationType(request.relationType());
        link.setActive(true);
        return parentStudentLinkRepository.save(link);
    }

    @Override
    @Transactional
    public ParentStudentLink revoke(Long id) {
        ParentStudentLink link = getById(id);
        if (link.isActive()) {
            link.setActive(false);
            link.setRevokedAt(Instant.now());
            link = parentStudentLinkRepository.save(link);
        }
        return link;
    }
}
