package com.okulservisi.backend.hostess.service.impl;

import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.hostess.dto.request.HostessCreateRequest;
import com.okulservisi.backend.hostess.dto.request.HostessUpdateRequest;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.mapper.HostessMapper;
import com.okulservisi.backend.hostess.repository.HostessRepository;
import com.okulservisi.backend.hostess.repository.HostessSpecifications;
import com.okulservisi.backend.hostess.service.HostessService;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.policy.UserProfileLinkValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** docs/API.md "6. Şoför/Hostes" — ADMIN CRUD, her Hostess bir User (role=HOSTESS) ile 1-1. */
@Service
@RequiredArgsConstructor
public class HostessServiceImpl implements HostessService {

    private final HostessRepository hostessRepository;
    private final UserProfileLinkValidator userProfileLinkValidator;
    private final HostessMapper hostessMapper;

    @Override
    public Page<Hostess> list(Boolean isActive, Pageable pageable) {
        Specification<Hostess> spec = Specification.allOf(HostessSpecifications.isActive(isActive));
        return hostessRepository.findAll(spec, pageable);
    }

    @Override
    public Hostess getById(Long id) {
        return hostessRepository.findById(id).orElseThrow(() -> NotFoundException.of("Hostes", id));
    }

    @Override
    public Hostess getByUserId(Long userId) {
        return hostessRepository.findByUserId(userId).orElseThrow(() -> NotFoundException.of("Hostes profili", userId));
    }

    @Override
    @Transactional
    public Hostess create(HostessCreateRequest request) {
        User user = userProfileLinkValidator.validateUserForNewProfile(request.userId(), Role.HOSTESS);
        if (hostessRepository.existsByUserId(user.getId())) {
            throw new ConflictException("Bu kullanıcı için zaten bir hostes profili var.");
        }
        Hostess hostess = new Hostess();
        hostess.setUser(user);
        hostess.setActive(true);
        return hostessRepository.save(hostess);
    }

    @Override
    @Transactional
    public Hostess update(Long id, HostessUpdateRequest request) {
        Hostess hostess = getById(id);
        hostessMapper.updateFromRequest(hostess, request);
        return hostessRepository.save(hostess);
    }
}
