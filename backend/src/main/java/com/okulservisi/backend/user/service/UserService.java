package com.okulservisi.backend.user.service;

import com.okulservisi.backend.user.dto.request.UserCreateRequest;
import com.okulservisi.backend.user.dto.request.UserRoleChangeRequest;
import com.okulservisi.backend.user.dto.request.UserUpdateRequest;
import com.okulservisi.backend.user.dto.response.UserCreateResponse;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * docs/API.md "2. Kullanıcı yönetimi" — ADMIN-only kullanıcı CRUD'u. Son aktif ADMIN korumalıdır
 * (docs/RBAC_MATRIX.md kural 2): sistemde en az bir aktif ADMIN kalmayacak işlemler 409 ile reddedilir.
 */
public interface UserService {

    Page<User> list(Role role, Boolean isActive, String q, Pageable pageable);

    /** Sprint 3: {@code studentLinked} filtresiyle (bkz. docs/API.md §3 "Öğrenci hesabı bağı"). */
    Page<User> list(Role role, Boolean isActive, String q, Boolean studentLinked, Pageable pageable);

    User getById(Long id);

    UserCreateResponse create(UserCreateRequest request);

    User update(Long id, UserUpdateRequest request);

    /**
     * docs/API.md "2. Kullanıcı yönetimi" — {@code POST /api/users/{id}/role}. Rol değişikliğini
     * (varsa) gerekli domain profilinin oluşturulması/yeniden kullanılmasıyla TEK bir veritabanı
     * transaction'ında birleştirir.
     */
    User changeRole(Long id, UserRoleChangeRequest request);

    User deactivate(Long id);

    User activate(Long id);
}
