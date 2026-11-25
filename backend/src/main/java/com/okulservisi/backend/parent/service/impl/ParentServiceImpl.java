package com.okulservisi.backend.parent.service.impl;

import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.parent.dto.request.ParentCreateRequest;
import com.okulservisi.backend.parent.dto.request.ParentUpdateRequest;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.mapper.ParentMapper;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.repository.ParentSpecifications;
import com.okulservisi.backend.parent.service.ParentService;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.policy.UserProfileLinkValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** docs/API.md "4. Veli" — ADMIN CRUD; her Parent bir User (role=PARENT) ile 1-1 (docs/DATABASE_DESIGN.md). */
@Service
@RequiredArgsConstructor
public class ParentServiceImpl implements ParentService {

    private final ParentRepository parentRepository;
    private final UserProfileLinkValidator userProfileLinkValidator;
    private final ParentMapper parentMapper;

    @Override
    public Page<Parent> list(String q, Pageable pageable) {
        Specification<Parent> spec = Specification.allOf(ParentSpecifications.matchesQuery(q));
        return parentRepository.findAll(spec, pageable);
    }

    @Override
    public Parent getById(Long id) {
        return parentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Veli", id));
    }

    @Override
    public Parent getByUserId(Long userId) {
        return parentRepository.findByUserId(userId).orElseThrow(() -> NotFoundException.of("Veli profili", userId));
    }

    @Override
    @Transactional
    public Parent create(ParentCreateRequest request) {
        User user = userProfileLinkValidator.validateUserForNewProfile(request.userId(), Role.PARENT);
        if (parentRepository.existsByUserId(user.getId())) {
            throw new ConflictException("Bu kullanıcı için zaten bir veli profili var.");
        }
        Parent parent = new Parent();
        parent.setUser(user);
        parent.setFullName(request.fullName());
        parent.setPhone(request.phone());
        parent.setTcNo(request.tcNo());
        return parentRepository.save(parent);
    }

    @Override
    @Transactional
    public Parent update(Long id, ParentUpdateRequest request) {
        Parent parent = getById(id);
        parentMapper.updateFromRequest(parent, request);
        return parentRepository.save(parent);
    }
}
