package com.okulservisi.backend.school.service;

import com.okulservisi.backend.school.dto.request.SchoolCreateRequest;
import com.okulservisi.backend.school.dto.request.SchoolUpdateRequest;
import com.okulservisi.backend.school.entity.School;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** docs/API.md §20 "Okul" — ADMIN CRUD, {@code name} benzersiz (büyük/küçük harf duyarsız). */
public interface SchoolService {

    Page<School> list(String q, Boolean isActive, Pageable pageable);

    School getById(Long id);

    School create(SchoolCreateRequest request);

    School update(Long id, SchoolUpdateRequest request);
}
