package com.okulservisi.backend.school.service.impl;

import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.school.dto.request.SchoolCreateRequest;
import com.okulservisi.backend.school.dto.request.SchoolUpdateRequest;
import com.okulservisi.backend.school.entity.School;
import com.okulservisi.backend.school.mapper.SchoolMapper;
import com.okulservisi.backend.school.repository.SchoolRepository;
import com.okulservisi.backend.school.repository.SchoolSpecifications;
import com.okulservisi.backend.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** docs/API.md §20 "Okul" — ADMIN CRUD, {@code name} benzersiz (büyük/küçük harf duyarsız). */
@Service
@RequiredArgsConstructor
public class SchoolServiceImpl implements SchoolService {

    private final SchoolRepository schoolRepository;
    private final SchoolMapper schoolMapper;

    @Override
    public Page<School> list(String q, Boolean isActive, Pageable pageable) {
        Specification<School> spec = Specification.allOf(
                SchoolSpecifications.matchesQuery(q),
                SchoolSpecifications.isActive(isActive));
        return schoolRepository.findAll(spec, pageable);
    }

    @Override
    public School getById(Long id) {
        return schoolRepository.findById(id).orElseThrow(() -> NotFoundException.of("Okul", id));
    }

    @Override
    @Transactional
    public School create(SchoolCreateRequest request) {
        if (schoolRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Bu isimde zaten bir okul kayıtlı.");
        }
        School school = new School();
        school.setName(request.name());
        school.setAddress(request.address());
        school.setPhone(request.phone());
        school.setActive(true);
        return schoolRepository.save(school);
    }

    @Override
    @Transactional
    public School update(Long id, SchoolUpdateRequest request) {
        School school = getById(id);
        if (request.name() != null) {
            if (!request.name().equalsIgnoreCase(school.getName()) && schoolRepository.existsByNameIgnoreCase(request.name())) {
                throw new ConflictException("Bu isimde zaten bir okul kayıtlı.");
            }
            school.setName(request.name());
        }
        schoolMapper.updateFromRequest(school, request);
        return schoolRepository.save(school);
    }
}
