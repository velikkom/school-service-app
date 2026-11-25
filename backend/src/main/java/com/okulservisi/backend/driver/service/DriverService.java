package com.okulservisi.backend.driver.service;

import com.okulservisi.backend.driver.dto.request.DriverCreateRequest;
import com.okulservisi.backend.driver.dto.request.DriverUpdateRequest;
import com.okulservisi.backend.driver.entity.Driver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** docs/API.md "6. Şoför/Hostes" — ADMIN CRUD, her Driver bir User (role=DRIVER) ile 1-1. */
public interface DriverService {

    Page<Driver> list(Boolean isActive, Pageable pageable);

    Driver getById(Long id);

    Driver getByUserId(Long userId);

    Driver create(DriverCreateRequest request);

    Driver update(Long id, DriverUpdateRequest request);
}
