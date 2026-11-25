package com.okulservisi.backend.hostess.service;

import com.okulservisi.backend.hostess.dto.request.HostessCreateRequest;
import com.okulservisi.backend.hostess.dto.request.HostessUpdateRequest;
import com.okulservisi.backend.hostess.entity.Hostess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** docs/API.md "6. Şoför/Hostes" — ADMIN CRUD, her Hostess bir User (role=HOSTESS) ile 1-1. */
public interface HostessService {

    Page<Hostess> list(Boolean isActive, Pageable pageable);

    Hostess getById(Long id);

    Hostess getByUserId(Long userId);

    Hostess create(HostessCreateRequest request);

    Hostess update(Long id, HostessUpdateRequest request);
}
