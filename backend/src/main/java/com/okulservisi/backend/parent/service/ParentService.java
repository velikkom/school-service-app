package com.okulservisi.backend.parent.service;

import com.okulservisi.backend.parent.dto.request.ParentCreateRequest;
import com.okulservisi.backend.parent.dto.request.ParentUpdateRequest;
import com.okulservisi.backend.parent.entity.Parent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** docs/API.md "4. Veli" — ADMIN CRUD; her Parent bir User (role=PARENT) ile 1-1 (docs/DATABASE_DESIGN.md). */
public interface ParentService {

    Page<Parent> list(String q, Pageable pageable);

    Parent getById(Long id);

    Parent getByUserId(Long userId);

    Parent create(ParentCreateRequest request);

    Parent update(Long id, ParentUpdateRequest request);
}
