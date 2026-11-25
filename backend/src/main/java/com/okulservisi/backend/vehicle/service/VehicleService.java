package com.okulservisi.backend.vehicle.service;

import com.okulservisi.backend.vehicle.dto.request.VehicleCreateRequest;
import com.okulservisi.backend.vehicle.dto.request.VehicleUpdateRequest;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** docs/API.md "5. Araç" — ADMIN CRUD, plaka normalize (büyük harf, boşluksuz) + unique. */
public interface VehicleService {

    Page<Vehicle> list(String q, Boolean isActive, Pageable pageable);

    Vehicle getById(Long id);

    /**
     * {@code GET /api/vehicles/mine} (SADECE DRIVER, docs/RBAC_MATRIX.md "kendi atanmış aracı, salt
     * okunur").
     *
     * @throws com.okulservisi.backend.common.exception.NotFoundException principal için {@code
     *     Driver} profili yoksa, veya profil var ama iptal edilmemiş hiçbir seferi yoksa.
     */
    Vehicle getActiveVehicleForDriver(Long userId);

    Vehicle create(VehicleCreateRequest request);

    Vehicle update(Long id, VehicleUpdateRequest request);
}
