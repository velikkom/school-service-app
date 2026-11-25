package com.okulservisi.backend.driver.service.impl;

import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.driver.dto.request.DriverCreateRequest;
import com.okulservisi.backend.driver.dto.request.DriverUpdateRequest;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.mapper.DriverMapper;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.driver.repository.DriverSpecifications;
import com.okulservisi.backend.driver.service.DriverService;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.policy.UserProfileLinkValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** docs/API.md "6. Şoför/Hostes" — ADMIN CRUD, her Driver bir User (role=DRIVER) ile 1-1. */
@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;
    private final UserProfileLinkValidator userProfileLinkValidator;
    private final DriverMapper driverMapper;

    @Override
    public Page<Driver> list(Boolean isActive, Pageable pageable) {
        Specification<Driver> spec = Specification.allOf(DriverSpecifications.isActive(isActive));
        return driverRepository.findAll(spec, pageable);
    }

    @Override
    public Driver getById(Long id) {
        return driverRepository.findById(id).orElseThrow(() -> NotFoundException.of("Şoför", id));
    }

    @Override
    public Driver getByUserId(Long userId) {
        return driverRepository.findByUserId(userId).orElseThrow(() -> NotFoundException.of("Şoför profili", userId));
    }

    @Override
    @Transactional
    public Driver create(DriverCreateRequest request) {
        User user = userProfileLinkValidator.validateUserForNewProfile(request.userId(), Role.DRIVER);
        if (driverRepository.existsByUserId(user.getId())) {
            throw new ConflictException("Bu kullanıcı için zaten bir şoför profili var.");
        }
        Driver driver = new Driver();
        driver.setUser(user);
        driver.setLicenseNo(request.licenseNo());
        driver.setActive(true);
        return driverRepository.save(driver);
    }

    @Override
    @Transactional
    public Driver update(Long id, DriverUpdateRequest request) {
        Driver driver = getById(id);
        driverMapper.updateFromRequest(driver, request);
        return driverRepository.save(driver);
    }
}
